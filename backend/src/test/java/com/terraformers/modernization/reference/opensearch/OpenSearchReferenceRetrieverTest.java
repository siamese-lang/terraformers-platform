package com.terraformers.modernization.reference.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.EmbeddingProvider;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenSearchReferenceRetrieverTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emptyResourceTypesPreserveSingleSemanticSearchAndOrdering() throws Exception {
        Fixture fixture = fixture(2, response(
                document("semantic-first", 10, "aws_alpha"),
                document("semantic-second", 20, "aws_beta")));

        List<ReferenceDocument> result = fixture.retriever().retrieve(
                new ReferenceQuery("architecture summary", List.of(), 2));

        assertThat(result).extracting(ReferenceDocument::id)
                .containsExactly("semantic-first", "semantic-second");
        verify(fixture.embedding(), times(1)).embed("architecture summary");
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(fixture.transport(), times(1)).post(
                eq(URI.create("https://search.example/references/_search")), bodies.capture());
        JsonNode body = objectMapper.readTree(bodies.getValue());
        assertThat(body.path("size").asInt()).isEqualTo(2);
        assertThat(body.path("query").toString()).contains("terraformers-reference-v2", "5.100.0");
        assertThat(body.path("query").toString()).doesNotContain("resourceTypes");
    }

    @Test
    void resourceAwarePathReusesOneEmbeddingAndUsesOneBoundedSearchPerResource() throws Exception {
        Fixture fixture = fixture(2,
                response(document("global", 1, "aws_alpha")),
                response(document("alpha", 1, "aws_alpha")),
                response(document("beta", 1, "aws_beta")));

        fixture.retriever().retrieve(new ReferenceQuery(
                "architecture summary", List.of("aws_alpha", "aws_beta", "aws_alpha"), 2));

        verify(fixture.embedding(), times(1)).embed("architecture summary");
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(fixture.transport(), times(3)).post(any(URI.class), bodies.capture());
        List<JsonNode> requests = bodies.getAllValues().stream().map(this::readTree).toList();
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path("size").asInt()).isEqualTo(2);
            assertThat(request.toString()).contains("[0.1,0.2]", "terraformers-reference-v2", "5.100.0");
        });
        assertThat(resourceFilter(requests.get(0))).containsExactly("aws_alpha", "aws_beta");
        assertThat(resourceFilter(requests.get(1))).containsExactly("aws_alpha");
        assertThat(resourceFilter(requests.get(2))).containsExactly("aws_beta");
    }

    @Test
    void selectsDeduplicatedTargetedEvidenceWithinTheExistingLimitDeterministically() {
        String global = response(
                document("shared", 1, "aws_alpha"),
                document("semantic", 1, "aws_alpha"));
        String alpha = response(document("shared", 1, "aws_alpha"));
        String beta = response(
                document("shared", 1, "aws_alpha"),
                document("targeted", 1, "aws_beta"));
        ReferenceQuery query = new ReferenceQuery(
                "architecture summary", List.of("aws_alpha", "aws_beta"), 2);

        List<ReferenceDocument> first = fixture(2, global, alpha, beta).retriever().retrieve(query);
        List<ReferenceDocument> second = fixture(2, global, alpha, beta).retriever().retrieve(query);

        assertThat(first).extracting(ReferenceDocument::id).containsExactly("shared", "targeted");
        assertThat(first).extracting(ReferenceDocument::id).doesNotHaveDuplicates();
        assertThat(first).hasSize(2);
        assertThat(second).extracting(ReferenceDocument::id)
                .containsExactlyElementsOf(first.stream().map(ReferenceDocument::id).toList());
    }

    @Test
    void higherPriorityBreaksEqualCoverageTie() {
        Fixture fixture = fixture(1,
                response(document("global", 1, "aws_alpha")),
                response(document("low-priority", 10, "aws_alpha"), document("high-priority", 90, "aws_alpha")));

        List<ReferenceDocument> result = fixture.retriever().retrieve(
                new ReferenceQuery("architecture summary", List.of("aws_alpha"), 1));

        // The global result already covers the request, so it is preserved rather than reranked.
        assertThat(result).extracting(ReferenceDocument::id).containsExactly("global");

        Fixture missingGlobally = fixture(2,
                response(document("unrelated-one", 1, "aws_other"), document("unrelated-two", 1, "aws_other")),
                response(document("low-priority", 10, "aws_alpha"), document("high-priority", 90, "aws_alpha")));
        assertThat(missingGlobally.retriever().retrieve(
                new ReferenceQuery("architecture summary", List.of("aws_alpha"), 2)))
                .extracting(ReferenceDocument::id).startsWith("high-priority");
    }

    @Test
    void preservesGlobalSetWhenItAlreadyCoversRequestedResources() {
        Fixture fixture = fixture(2,
                response(document("first", 1, "aws_alpha"), document("second", 1, "aws_beta")),
                response(document("target-alpha", 100, "aws_alpha")),
                response(document("target-beta", 100, "aws_beta")));

        assertThat(fixture.retriever().retrieve(new ReferenceQuery(
                "architecture summary", List.of("aws_alpha", "aws_beta"), 2)))
                .extracting(ReferenceDocument::id).containsExactly("first", "second");
    }

    @Test
    void propagatesEmbeddingTransportAndParserFailures() {
        Fixture embeddingFailure = fixture(2, response());
        when(embeddingFailure.embedding().embed(any())).thenThrow(new IllegalStateException("embedding failed"));
        assertThatThrownBy(() -> embeddingFailure.retriever().retrieve(
                new ReferenceQuery("architecture summary", List.of(), 2)))
                .hasMessage("embedding failed");
        verify(embeddingFailure.transport(), never()).post(any(), any());

        Fixture transportFailure = fixture(2, response());
        when(transportFailure.transport().post(any(), any())).thenThrow(new IllegalStateException("transport failed"));
        assertThatThrownBy(() -> transportFailure.retriever().retrieve(
                new ReferenceQuery("architecture summary", List.of(), 2)))
                .hasMessage("transport failed");

        Fixture parserFailure = fixture(2, "not-json");
        assertThatThrownBy(() -> parserFailure.retriever().retrieve(
                new ReferenceQuery("architecture summary", List.of(), 2)))
                .hasMessageContaining("failed to parse OpenSearch response");
    }

    @Test
    void rejectsInvalidActiveConfigurationBeforeCallingDependencies() {
        AnalysisRuntimeProperties properties = activeProperties(2);
        properties.setOpensearchTopK(0);
        EmbeddingProvider embedding = mock(EmbeddingProvider.class);
        OpenSearchTransport transport = mock(OpenSearchTransport.class);
        OpenSearchReferenceRetriever retriever = new OpenSearchReferenceRetriever(
                embedding, new OpenSearchKnnQueryBuilder(objectMapper), new OpenSearchResponseParser(objectMapper),
                transport, properties);

        assertThatThrownBy(() -> retriever.retrieve(new ReferenceQuery("architecture summary", 2)))
                .hasMessageContaining("top-k must be positive");
        verify(embedding, never()).embed(any());
        verify(transport, never()).post(any(), any());
    }

    private Fixture fixture(int limit, String... responses) {
        EmbeddingProvider embedding = mock(EmbeddingProvider.class);
        when(embedding.embed(any())).thenReturn(List.of(0.1f, 0.2f));
        OpenSearchTransport transport = mock(OpenSearchTransport.class);
        Queue<String> queuedResponses = new ArrayDeque<>(List.of(responses));
        when(transport.post(any(), any())).thenAnswer(invocation -> queuedResponses.remove());
        OpenSearchReferenceRetriever retriever = new OpenSearchReferenceRetriever(
                embedding,
                new OpenSearchKnnQueryBuilder(objectMapper),
                new OpenSearchResponseParser(objectMapper),
                transport,
                activeProperties(limit));
        return new Fixture(retriever, embedding, transport);
    }

    private AnalysisRuntimeProperties activeProperties(int limit) {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setOpensearchEndpoint("https://search.example");
        properties.setIndexName("references");
        properties.setVectorFieldName("embedding");
        properties.setContentFieldName("content");
        properties.setCorpusVersion("terraformers-reference-v2");
        properties.setProviderVersion("5.100.0");
        properties.setOpensearchTopK(limit);
        return properties;
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private List<String> resourceFilter(JsonNode request) {
        JsonNode filters = request.path("query").path("knn").path("embedding").path("filter")
                .path("bool").path("filter");
        for (JsonNode filter : filters) {
            JsonNode values = filter.path("terms").path("resourceTypes");
            if (values.isArray()) {
                return objectMapper.convertValue(values, objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class));
            }
        }
        return List.of();
    }

    private String response(String... documents) {
        return "{\"hits\":{\"hits\":[" + String.join(",", documents) + "]}}";
    }

    private String document(String id, int priority, String... resourceTypes) {
        String resources = java.util.Arrays.stream(resourceTypes)
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"_score\":1.0,\"_source\":{\"documentId\":\"" + id
                + "\",\"title\":\"" + id + "\",\"content\":\"content\",\"priority\":" + priority
                + ",\"resourceTypes\":[" + resources + "]}}";
    }

    private record Fixture(
            OpenSearchReferenceRetriever retriever,
            EmbeddingProvider embedding,
            OpenSearchTransport transport
    ) {
    }
}
