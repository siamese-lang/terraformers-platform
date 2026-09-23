package com.terraformers.modernization.reference.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.EmbeddingProvider;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OpenSearchReferenceRetrieverTest {
    @Test
    void runsFilteredEmbeddingKnnSignedSearchAndResponseParsingInOrder() {
        EmbeddingProvider embedding = mock(EmbeddingProvider.class);
        OpenSearchKnnQueryBuilder queryBuilder = mock(OpenSearchKnnQueryBuilder.class);
        OpenSearchResponseParser parser = mock(OpenSearchResponseParser.class);
        OpenSearchTransport transport = mock(OpenSearchTransport.class);
        AnalysisRuntimeProperties properties = activeProperties();
        List<Float> vector = List.of(0.1f, 0.2f);
        when(embedding.embed(any())).thenReturn(vector);
        when(queryBuilder.build(
                eq("embedding"),
                eq("content"),
                eq(vector),
                eq(2),
                eq("terraformers-reference-v2"),
                eq("5.100.0"),
                eq(List.of("aws_vpc"))
        )).thenReturn("knn-body");
        when(transport.post(any(URI.class), eq("knn-body"))).thenReturn("response");
        List<ReferenceDocument> expected = List.of(new ReferenceDocument("ref-1", "title", "content", 1.0));
        when(parser.parse("response", "content")).thenReturn(expected);

        OpenSearchReferenceRetriever retriever = new OpenSearchReferenceRetriever(embedding, queryBuilder, parser, transport, properties);
        assertThat(retriever.retrieve(query())).isEqualTo(expected);

        InOrder order = inOrder(embedding, queryBuilder, transport, parser);
        order.verify(embedding).embed(any());
        order.verify(queryBuilder).build(
                "embedding",
                "content",
                vector,
                2,
                "terraformers-reference-v2",
                "5.100.0",
                List.of("aws_vpc")
        );
        order.verify(transport).post(URI.create("https://search.example/references/_search"), "knn-body");
        order.verify(parser).parse("response", "content");
    }

    @Test
    void rejectsInvalidActiveConfigurationBeforeCallingDependencies() {
        AnalysisRuntimeProperties properties = activeProperties();
        properties.setOpensearchTopK(0);
        OpenSearchReferenceRetriever retriever = new OpenSearchReferenceRetriever(
                mock(EmbeddingProvider.class), mock(OpenSearchKnnQueryBuilder.class), mock(OpenSearchResponseParser.class),
                mock(OpenSearchTransport.class), properties);
        assertThatThrownBy(() -> retriever.retrieve(query())).hasMessageContaining("top-k must be positive");
    }

    @Test
    void remainsUnawareOfAwsSigningConfiguration() {
        AnalysisRuntimeProperties properties = activeProperties();
        OpenSearchTransport transport = mock(OpenSearchTransport.class);
        OpenSearchKnnQueryBuilder queryBuilder = mock(OpenSearchKnnQueryBuilder.class);
        when(queryBuilder.build(any(), any(), any(), any(Integer.class), any(), any(), any())).thenReturn("body");
        when(transport.post(any(), any())).thenReturn("response");
        OpenSearchResponseParser parser = mock(OpenSearchResponseParser.class);
        when(parser.parse(any(), any())).thenReturn(List.of());

        OpenSearchReferenceRetriever retriever = new OpenSearchReferenceRetriever(
                text -> List.of(0.1f),
                queryBuilder,
                parser,
                transport,
                properties);

        assertThatCode(() -> retriever.retrieve(query())).doesNotThrowAnyException();
    }

    private AnalysisRuntimeProperties activeProperties() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setOpensearchEndpoint("https://search.example");
        properties.setIndexName("references");
        properties.setVectorFieldName("embedding");
        properties.setContentFieldName("content");
        properties.setCorpusVersion("terraformers-reference-v2");
        properties.setProviderVersion("5.100.0");
        properties.setOpensearchTopK(2);
        return properties;
    }

    private ReferenceQuery query() {
        return new ReferenceQuery("VPC private subnet using aws_vpc to RDS", 2);
    }
}
