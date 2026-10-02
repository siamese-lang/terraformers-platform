package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.analysis.GeneratedTerraformContractInspector;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collection;
import org.mockito.ArgumentCaptor;
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
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(new AnalysisGenerationResult(
                "vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0,
                "resource \"aws_vpc\" \"main\" {}", "Three tier", List.of("VPC"), List.of(), List.of(),
                "STOP", 10, false
        ));
        VertexAnalysisProvider provider = new VertexAnalysisProvider(
                objectReader, retriever, properties, factsExtractor, textWithoutResourceIdentifiers, generationStage,
                new TerraformDraftValidator(), schemaCatalog(), mock(GeneratedTerraformContractInspector.class));

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
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(generatedArchitecture());
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
            when(generationStage.generate(any(), any(), any(), any())).thenThrow(
                    new AnalysisInputRejectedException(classification, 0.9, false, null));
            VertexAnalysisProvider provider = provider(query -> List.of(), generationStage);

            assertThatThrownBy(() -> provider.analyze(context()))
                    .isInstanceOfSatisfying(AnalysisProviderFailureException.class,
                            failure -> assertThat(failure.reason())
                                    .isEqualTo(AnalysisProviderFailureReason.INPUT_REJECTED))
                    .hasCauseInstanceOf(AnalysisInputRejectedException.class);
        }
    }

    @Test
    void safeFirstGenerationDoesNotRecoverAndRunsUpstreamStagesOnce() {
        ReferenceRetriever retriever = mock(ReferenceRetriever.class);
        List<ReferenceDocument> references = List.of(reference());
        when(retriever.retrieve(any())).thenReturn(references);
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(safeGeneration());
        ProviderFixture fixture = fixture(retriever, generationStage);
        AnalysisRequestContext context = context();

        fixture.provider().analyze(context);

        verify(generationStage, times(1)).generate(same(context), same(fixture.source()), same(references), any());
        verify(generationStage, never()).regenerateAfterSensitiveCredential(any(), any(), any());
        verify(fixture.factsExtractor(), times(1)).extract(same(fixture.source()));
        verify(retriever, times(1)).retrieve(any());
    }

    @Test
    void sensitiveFirstGenerationRecoversOnceWithSameSourceAndReferences() {
        ReferenceRetriever retriever = mock(ReferenceRetriever.class);
        List<ReferenceDocument> references = List.of(reference());
        when(retriever.retrieve(any())).thenReturn(references);
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(sensitiveGeneration());
        when(generationStage.regenerateAfterSensitiveCredential(any(), any(), any(), any())).thenReturn(safeGeneration());
        ProviderFixture fixture = fixture(retriever, generationStage);
        AnalysisRequestContext context = context();

        var result = fixture.provider().analyze(context);

        assertThat(result.terraformCode()).isEqualTo(safeGeneration().terraformCode());
        verify(generationStage, times(1)).generate(same(context), same(fixture.source()), same(references), any());
        verify(generationStage, times(1)).regenerateAfterSensitiveCredential(
                same(context), same(fixture.source()), same(references), any());
        verify(fixture.factsExtractor(), times(1)).extract(same(fixture.source()));
        verify(retriever, times(1)).retrieve(any());
    }

    @Test
    void unrelatedInvalidFirstGenerationDoesNotRecover() {
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(generation("placeholder output"));
        VertexAnalysisProvider provider = provider(query -> List.of(reference()), generationStage);

        provider.analyze(context());

        verify(generationStage, never()).regenerateAfterSensitiveCredential(any(), any(), any());
    }

    @Test
    void sensitiveRecoveryIsReturnedWithoutSecondProviderRetry() {
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(sensitiveGeneration());
        when(generationStage.regenerateAfterSensitiveCredential(any(), any(), any(), any()))
                .thenReturn(sensitiveGeneration());
        VertexAnalysisProvider provider = provider(query -> List.of(reference()), generationStage);

        var result = provider.analyze(context());

        verify(generationStage, times(1)).regenerateAfterSensitiveCredential(any(), any(), any(), any());
        assertThat(new TerraformDraftValidator().isHardCodedSensitiveCredentialFailure(
                new TerraformDraftValidator().validate(result.terraformCode()))).isTrue();
    }

    @Test
    void truncationRetryThenSensitiveResultDoesNotMakeThirdCall() {
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        AnalysisGenerationResult secondAttemptSensitive = new AnalysisGenerationResult(
                "vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0,
                sensitiveGeneration().terraformCode(), "VPC", List.of(), List.of(), List.of(), "STOP", 10, true);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(secondAttemptSensitive);

        provider(query -> List.of(reference()), generationStage).analyze(context());

        verify(generationStage, never()).regenerateAfterSensitiveCredential(any(), any(), any(), any());
    }

    @Test
    void combinesFactAndRetrievedCompanionTypesIntoGenerationSchemaEvidence() {
        ObjectContent source = source();
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source);
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(source)).thenReturn(new ArchitectureRetrievalFacts(
                "VPC", List.of("VPC"), List.of(), List.of("aws_vpc")));
        ReferenceDocument companion = new ReferenceDocument(
                "network-pattern", "Network pattern", "Use a subnet", 1.0, "PROJECT_DECISION",
                List.of("aws_subnet"), "network.md", "5.100.0", "v3", "PROJECT", 1, List.of());
        ReferenceRetriever retriever = query -> List.of(companion);
        AwsProviderSchemaEvidence expectedEvidence = new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block: type=\"string\" (optional)",
                "aws_subnet", "vpc_id: type=\"string\" (required)"));
        AwsProviderSchemaCatalog catalog = mock(AwsProviderSchemaCatalog.class);
        when(catalog.resolve(any())).thenReturn(expectedEvidence);
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), same(expectedEvidence))).thenReturn(safeGeneration());
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        VertexAnalysisProvider provider = new VertexAnalysisProvider(
                objectReader, retriever, properties, factsExtractor, new RetrievalQueryTextBuilder(), generationStage,
                new TerraformDraftValidator(), catalog, mock(GeneratedTerraformContractInspector.class));

        provider.analyze(context());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> candidates = ArgumentCaptor.forClass(Collection.class);
        verify(catalog).resolve(candidates.capture());
        assertThat(candidates.getValue()).containsExactly("aws_vpc", "aws_subnet");
        verify(generationStage).generate(any(), same(source), eq(List.of(companion)), same(expectedEvidence));
        assertThat(expectedEvidence.resourceTypes()).containsExactlyInAnyOrder("aws_vpc", "aws_subnet");
    }

    @Test
    void unknownFactSelectedAwsResourceFailsBeforeGeneration() {
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source());
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(any())).thenReturn(new ArchitectureRetrievalFacts(
                "Unknown", List.of(), List.of(), List.of("aws_not_real")));
        AwsProviderSchemaCatalog catalog = mock(AwsProviderSchemaCatalog.class);
        when(catalog.resolve(any())).thenThrow(new IllegalArgumentException(
                "candidate is absent from AWS 5.100.0 provider schema: aws_not_real"));
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        VertexAnalysisProvider provider = new VertexAnalysisProvider(
                objectReader, query -> List.of(reference()), properties, factsExtractor,
                new RetrievalQueryTextBuilder(), generationStage, new TerraformDraftValidator(), catalog,
                mock(GeneratedTerraformContractInspector.class));

        assertThatThrownBy(() -> provider.analyze(context()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aws_not_real");
        verify(generationStage, never()).generate(any(), any(), any(), any());
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
                new RetrievalQueryTextBuilder(), generationStage, new TerraformDraftValidator(), schemaCatalog(),
                mock(GeneratedTerraformContractInspector.class));
    }

    private ProviderFixture fixture(ReferenceRetriever retriever, VertexGenerationStage generationStage) {
        ObjectContent source = source();
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source);
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(source)).thenReturn(new ArchitectureRetrievalFacts(
                "VPC", List.of("VPC"), List.of(), List.of("aws_vpc")));
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        return new ProviderFixture(new VertexAnalysisProvider(
                objectReader, retriever, properties, factsExtractor, new RetrievalQueryTextBuilder(),
                generationStage, new TerraformDraftValidator(), schemaCatalog(),
                mock(GeneratedTerraformContractInspector.class)), source, factsExtractor);
    }

    private AwsProviderSchemaCatalog schemaCatalog() {
        AwsProviderSchemaCatalog catalog = mock(AwsProviderSchemaCatalog.class);
        when(catalog.resolve(any())).thenReturn(new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block(optional)",
                "aws_db_instance", "password(optional)",
                "aws_security_group", "name(optional)")));
        return catalog;
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

    private AnalysisGenerationResult safeGeneration() {
        return generation("resource \"aws_vpc\" \"main\" { cidr_block = \"10.0.0.0/16\" }");
    }

    private AnalysisGenerationResult sensitiveGeneration() {
        return generation("resource \"aws_db_instance\" \"main\" { password = \"unsafe-example\" }");
    }

    private AnalysisGenerationResult generation(String terraform) {
        return new AnalysisGenerationResult(
                "vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0,
                terraform, "VPC", List.of("VPC"), List.of(), List.of(), "STOP", 10, false);
    }

    private ReferenceDocument reference() {
        return new ReferenceDocument("ref", "title", "content", 1.0);
    }

    private AnalysisRequestContext context() {
        return new AnalysisRequestContext(
                "job", "project", "bucket", "key.png", "correlation", AnalysisMode.INTEGRATED_JAVA);
    }

    private record ProviderFixture(
            VertexAnalysisProvider provider,
            ObjectContent source,
            VertexArchitectureFactsExtractor factsExtractor
    ) {}
}
