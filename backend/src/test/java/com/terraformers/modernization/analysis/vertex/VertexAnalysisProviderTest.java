package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import com.terraformers.modernization.storage.ObjectReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VertexAnalysisProviderTest {

    @Test
    void passesStructuredFactResourceTypesToRetrievalWithoutParsingQueryText() {
        ObjectContent source = new ObjectContent(
                new ObjectMetadata("bucket", "key.png", "image/png", 3, "etag"),
                new byte[] {1, 2, 3}
        );
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source);
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(source)).thenReturn(new ArchitectureRetrievalFacts(
                "Three tier",
                List.of("VPC", "Database"),
                List.of("VPC -> Database"),
                List.of("aws_vpc", "aws_db_instance", "aws_security_group")
        ));
        RetrievalQueryTextBuilder textWithoutResourceIdentifiers = new RetrievalQueryTextBuilder() {
            @Override
            public String build(ArchitectureRetrievalFacts facts) {
                return "architecture facts without resource identifiers";
            }
        };
        AtomicReference<ReferenceQuery> receivedQuery = new AtomicReference<>();
        ReferenceRetriever retriever = query -> {
            receivedQuery.set(query);
            return List.of(new ReferenceDocument("ref", "title", "content", 1.0));
        };
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        properties.setOpensearchTopK(8);
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any())).thenReturn(new AnalysisGenerationResult(
                "vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0,
                "resource \"aws_vpc\" \"main\" {}", "Three tier", List.of("VPC"), List.of(), List.of(),
                "STOP", 10, false
        ));
        VertexAnalysisProvider provider = new VertexAnalysisProvider(
                objectReader, retriever, properties, factsExtractor, textWithoutResourceIdentifiers, generationStage);

        provider.analyze(new AnalysisRequestContext(
                "job", "project", "bucket", "key.png", "correlation", AnalysisMode.INTEGRATED_JAVA));

        assertThat(receivedQuery.get()).isNotNull();
        assertThat(receivedQuery.get().text()).doesNotContain("aws_");
        assertThat(receivedQuery.get().resourceTypes())
                .containsExactly("aws_vpc", "aws_db_instance", "aws_security_group");
        assertThat(receivedQuery.get().limit()).isEqualTo(properties.getOpensearchMaxEvidence());
    }

    @Test
    void requiredArchitectureGenerationFailsClosedWhenRetrievalIsEmpty() {
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any())).thenReturn(generatedArchitecture());
        VertexAnalysisProvider provider = provider(query -> List.of(), generationStage);

        assertThatThrownBy(() -> provider.analyze(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required grounding");
    }

    @Test
    void emptyRetrievalPreservesNonArchitectureAndAmbiguousRejection() {
        for (AnalysisInputClassification classification : List.of(
                AnalysisInputClassification.NON_ARCHITECTURE_IMAGE,
                AnalysisInputClassification.AMBIGUOUS)) {
            VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
            when(generationStage.generate(any(), any(), any())).thenThrow(
                    new AnalysisInputRejectedException(classification, 0.9, false, null));
            VertexAnalysisProvider provider = provider(query -> List.of(), generationStage);

            assertThatThrownBy(() -> provider.analyze(context()))
                    .isInstanceOfSatisfying(AnalysisProviderFailureException.class,
                            failure -> assertThat(failure.reason())
                                    .isEqualTo(AnalysisProviderFailureReason.INPUT_REJECTED))
                    .hasCauseInstanceOf(AnalysisInputRejectedException.class);
        }
    }

    private VertexAnalysisProvider provider(
            ReferenceRetriever retriever,
            VertexGenerationStage generationStage
    ) {
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source());
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(any())).thenReturn(new ArchitectureRetrievalFacts(
                "VPC", List.of("VPC"), List.of(), List.of("aws_vpc")));
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        return new VertexAnalysisProvider(objectReader, retriever, properties, factsExtractor,
                new RetrievalQueryTextBuilder(), generationStage);
    }

    private ObjectContent source() {
        return new ObjectContent(
                new ObjectMetadata("bucket", "key.png", "image/png", 3, "etag"),
                new byte[] {1, 2, 3}
        );
    }

    private AnalysisGenerationResult generatedArchitecture() {
        return new AnalysisGenerationResult(
                "vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0,
                "resource \"aws_vpc\" \"main\" {}", "VPC", List.of("VPC"), List.of(), List.of(),
                "STOP", 10, false
        );
    }

    private AnalysisRequestContext context() {
        return new AnalysisRequestContext(
                "job", "project", "bucket", "key.png", "correlation", AnalysisMode.INTEGRATED_JAVA);
    }
}
