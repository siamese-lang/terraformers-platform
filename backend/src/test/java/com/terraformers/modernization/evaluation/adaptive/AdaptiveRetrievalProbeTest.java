package com.terraformers.modernization.evaluation.adaptive;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchKnnQueryBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchResponseParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdaptiveRetrievalProbeTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void frozenFixtureUsesRealCorpusAndNeedsTenDocumentsForTwelveResources() throws Exception {
        AdaptiveRetrievalProbeFixture fixture = fixture();
        assertThat(fixture.facts().resourceTypes()).hasSize(12);
        assertThat(fixture.minimumCorpusDocumentCover()).isEqualTo(10);

        List<JsonNode> corpus = Files.readAllLines(Path.of("../corpus/terraformers-reference/v3/documents.jsonl"))
                .stream()
                .filter(line -> !line.isBlank())
                .map(this::readTree)
                .toList();

        for (String resourceType : fixture.facts().resourceTypes()) {
            assertThat(corpus.stream().anyMatch(document ->
                    textList(document.path("resourceTypes")).contains(resourceType)))
                    .as("corpus contains %s", resourceType)
                    .isTrue();
        }

        assertThat(minimumDocumentCover(corpus, fixture.facts().resourceTypes()))
                .isEqualTo(fixture.minimumCorpusDocumentCover())
                .isGreaterThan(AdaptiveRetrievalProbeRunner.BASE_TOP_K);
    }

    @Test
    void excludesSharedEmbeddingTimeFromBothArmLatencies() {
        AdaptiveRetrievalProbeFixture fixture = fixture();
        AtomicInteger embeddingCalls = new AtomicInteger();
        List<String> responses = queuedResponses(fixture.facts().resourceTypes());
        int[] responseIndex = {0};
        long[] now = {0L};

        AdaptiveRetrievalProbeRunner runner = new AdaptiveRetrievalProbeRunner(
                text -> {
                    embeddingCalls.incrementAndGet();
                    now[0] += 1_000_000_000L;
                    return java.util.Collections.nCopies(1024, 0.25f);
                },
                new OpenSearchKnnQueryBuilder(mapper),
                new OpenSearchResponseParser(mapper),
                (uri, body) -> {
                    now[0] += 10_000_000L;
                    return responses.get(responseIndex[0]++);
                },
                properties(),
                new RetrievalQueryTextBuilder(),
                () -> now[0]
        );

        AdaptiveRetrievalProbeReport report = runner.run(
                fixture, "a".repeat(40), "gemini-embedding-001", 1024);

        assertThat(embeddingCalls).hasValue(1);
        assertThat(report.embeddingDelegateCalls()).isEqualTo(1);
        assertThat(report.control().latencyMs()).isEqualTo(260L);
        assertThat(report.adaptive().latencyMs()).isEqualTo(260L);
    }

    @Test
    void productionRetrieverRecoversTwelveResourceCoverageOnlyWithAdaptiveGrowth() {
        AdaptiveRetrievalProbeFixture fixture = fixture();
        AtomicInteger embeddingCalls = new AtomicInteger();
        List<String> requests = new ArrayList<>();
        List<String> responses = queuedResponses(fixture.facts().resourceTypes());
        int[] responseIndex = {0};

        AdaptiveRetrievalProbeRunner runner = new AdaptiveRetrievalProbeRunner(
                text -> {
                    embeddingCalls.incrementAndGet();
                    return java.util.Collections.nCopies(1024, 0.25f);
                },
                new OpenSearchKnnQueryBuilder(mapper),
                new OpenSearchResponseParser(mapper),
                (uri, body) -> {
                    requests.add(body);
                    return responses.get(responseIndex[0]++);
                },
                properties(),
                new RetrievalQueryTextBuilder()
        );

        AdaptiveRetrievalProbeReport report = runner.run(
                fixture, "a".repeat(40), "gemini-embedding-001", 1024);

        assertThat(embeddingCalls).hasValue(1);
        assertThat(report.embeddingDelegateCalls()).isEqualTo(1);
        assertThat(report.queryResourceTypes()).containsExactlyElementsOf(fixture.facts().resourceTypes());

        assertThat(report.control().evidenceCount()).isEqualTo(8);
        assertThat(report.control().coverageMatched()).isEqualTo(8);
        assertThat(report.control().coverageTotal()).isEqualTo(12);
        assertThat(report.control().missingResourceTypes())
                .containsExactlyElementsOf(fixture.facts().resourceTypes().subList(8, 12));

        assertThat(report.adaptive().evidenceCount()).isEqualTo(12);
        assertThat(report.adaptive().coverageMatched()).isEqualTo(12);
        assertThat(report.adaptive().coverageTotal()).isEqualTo(12);
        assertThat(report.adaptive().missingResourceTypes()).isEmpty();
        assertThat(new LinkedHashSet<>(report.adaptive().selectedDocumentIds()))
                .hasSize(report.adaptive().evidenceCount());

        assertThat(requests).hasSize(52);
        assertThat(requests.stream().map(this::readTree)
                .allMatch(request -> request.path("size").asInt() == AdaptiveRetrievalProbeRunner.BASE_TOP_K))
                .isTrue();
    }

    private AdaptiveRetrievalProbeFixture fixture() {
        return AdaptiveRetrievalProbeFixture.load(
                mapper, Path.of("../evaluation/adaptive-retrieval-v1/fixed-facts.json"));
    }

    private AnalysisRuntimeProperties properties() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setOpensearchEndpoint("http://example");
        properties.setIndexName("index");
        properties.setVectorFieldName("embedding");
        properties.setContentFieldName("content");
        properties.setCorpusVersion("terraformers-reference-v3");
        properties.setProviderVersion("5.100.0");
        properties.setExpectedVectorDimension(1024);
        properties.setOpensearchTopK(8);
        properties.setOpensearchMaxEvidence(16);
        return properties;
    }

    private List<String> queuedResponses(List<String> resources) {
        List<String> responses = new ArrayList<>();
        for (int arm = 0; arm < 2; arm++) {
            responses.add(response(java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> document("global-" + (index + 1), resources.get(index)))
                    .toList()));
            for (int index = 0; index < resources.size(); index++) {
                responses.add(response(List.of(document("target-" + (index + 1), resources.get(index)))));
            }
            responses.add(response(List.of()));
            // This historical fixture has schema-only evidence: each requested resource now
            // also receives one bounded official-document lookup, returning no hits.
            for (String resource : resources) {
                responses.add(response(List.of()));
            }
        }
        return List.copyOf(responses);
    }

    private ReferenceDocument document(String id, String resourceType) {
        return new ReferenceDocument(
                id, id, "content", 1.0, "RESOURCE",
                List.of(resourceType), "source", "5.100.0",
                "terraformers-reference-v3", "PROVIDER_SCHEMA", 1, List.of());
    }

    private String response(List<ReferenceDocument> documents) {
        try {
            List<Map<String, Object>> hits = documents.stream().map(document -> Map.<String, Object>of(
                    "_score", document.score(),
                    "_source", Map.ofEntries(
                            Map.entry("documentId", document.id()),
                            Map.entry("title", document.title()),
                            Map.entry("content", document.content()),
                            Map.entry("documentType", document.documentType()),
                            Map.entry("resourceTypes", document.resourceTypes()),
                            Map.entry("sourcePath", document.sourcePath()),
                            Map.entry("providerVersion", document.providerVersion()),
                            Map.entry("corpusVersion", document.corpusVersion()),
                            Map.entry("authority", document.authority()),
                            Map.entry("priority", document.priority()),
                            Map.entry("riskTags", document.riskTags())
                    )
            )).toList();
            return mapper.writeValueAsString(Map.of("hits", Map.of("hits", hits)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private int minimumDocumentCover(List<JsonNode> corpus, List<String> targetResources) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < targetResources.size(); i++) {
            index.put(targetResources.get(i), i);
        }
        int fullMask = (1 << targetResources.size()) - 1;
        Map<Integer, Integer> minimum = new HashMap<>();
        minimum.put(0, 0);

        for (JsonNode document : corpus) {
            int documentMask = 0;
            for (String resourceType : textList(document.path("resourceTypes"))) {
                Integer bit = index.get(resourceType);
                if (bit != null) {
                    documentMask |= 1 << bit;
                }
            }
            if (documentMask == 0) {
                continue;
            }
            List<Map.Entry<Integer, Integer>> snapshot = new ArrayList<>(minimum.entrySet());
            for (Map.Entry<Integer, Integer> state : snapshot) {
                int combined = state.getKey() | documentMask;
                int count = state.getValue() + 1;
                minimum.merge(combined, count, Math::min);
            }
        }
        return minimum.getOrDefault(fullMask, Integer.MAX_VALUE);
    }

    private JsonNode readTree(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual()) {
                values.add(value.asText());
            }
        });
        return List.copyOf(values);
    }
}
