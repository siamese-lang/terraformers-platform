package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VertexAnalysisProviderTest {

    @Test
    void passesStructuredFactResourceTypesToRetrievalWithoutParsingQueryText() {
        ObjectContent source = source();
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
            return List.of(reference());
        };
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(safeGeneration());
        AwsProviderSchemaCatalog catalog = catalogWith(
                List.of("aws_vpc", "aws_db_instance", "aws_security_group"),
                new AwsProviderSchemaEvidence(Map.of("aws_vpc", "cidr_block(optional)")));
        AnalysisRuntimeProperties properties = requiredProperties();
        VertexAnalysisProvider provider = new VertexAnalysisProvider(
                objectReader, retriever, properties, factsExtractor, textWithoutResourceIdentifiers,
                generationStage, catalog, mock(GeneratedTerraformContractInspector.class));

        provider.analyze(context());

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
    void policyLikeLiteralDraftDoesNotTriggerASecondGenerationPath() {
        ReferenceRetriever retriever = query -> List.of(reference());
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        AnalysisGenerationResult literalDraft = generation("""
                resource "aws_db_instance" "main" {
                  password = "example-password"
                }
                """);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(literalDraft);
        GeneratedTerraformContractInspector inspector = mock(GeneratedTerraformContractInspector.class);
        VertexAnalysisProvider provider = provider(retriever, generationStage, inspector);

        var result = provider.analyze(context());

        assertThat(result.terraformCode()).contains("example-password");
        verify(generationStage, times(1)).generate(any(), any(), any(), any());
        verify(inspector).inspect(literalDraft.terraformCode());
    }

    @Test
    void combinesFactMetadataAndReferenceContentResourcesIntoPromptSchemaContext() {
        ObjectContent source = source();
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source);
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(source)).thenReturn(new ArchitectureRetrievalFacts(
                "VPC", List.of("VPC"), List.of(), List.of("aws_vpc")));

        ReferenceDocument companion = new ReferenceDocument(
                "network-pattern",
                "Network pattern",
                """
                Use a subnet and listener:
                resource "aws_lb_listener" "http" {
                  load_balancer_arn = aws_lb.app.arn
                  port = 80
                  protocol = "HTTP"
                }
                """,
                1.0,
                "TERRAFORMERS_PATTERN",
                List.of("aws_subnet"),
                "network.md",
                "5.100.0",
                "v3",
                "PROJECT_DECISION",
                1,
                List.of()
        );
        ReferenceRetriever retriever = query -> List.of(companion);
        AwsProviderSchemaEvidence expectedEvidence = new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block: type=\"string\" (optional)",
                "aws_subnet", "vpc_id: type=\"string\" (required)",
                "aws_lb_listener", "port: type=\"number\" (optional)"));
        AwsProviderSchemaCatalog catalog = catalogWith(
                List.of("aws_vpc", "aws_subnet", "aws_lb_listener"), expectedEvidence);
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), same(expectedEvidence))).thenReturn(safeGeneration());

        VertexAnalysisProvider provider = new VertexAnalysisProvider(
                objectReader, retriever, requiredProperties(), factsExtractor,
                new RetrievalQueryTextBuilder(), generationStage, catalog,
                mock(GeneratedTerraformContractInspector.class));

        provider.analyze(context());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> candidates = ArgumentCaptor.forClass(Collection.class);
        verify(catalog).resolve(candidates.capture());
        assertThat(candidates.getValue()).containsExactly("aws_vpc", "aws_subnet", "aws_lb_listener");
        verify(generationStage).generate(any(), same(source), eq(List.of(companion)), same(expectedEvidence));
    }

    @Test
    void unknownFactResourceIsOmittedFromAdvisorySchemaContextInsteadOfFailingRequest() {
        ObjectContent source = source();
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source);
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(source)).thenReturn(new ArchitectureRetrievalFacts(
                "Unknown", List.of(), List.of(), List.of("aws_not_real")));

        ReferenceDocument reference = new ReferenceDocument(
                "vpc-example", "VPC", "resource \"aws_vpc\" \"main\" { cidr_block = \"10.0.0.0/16\" }", 1.0);
        AwsProviderSchemaEvidence evidence =
                new AwsProviderSchemaEvidence(Map.of("aws_vpc", "cidr_block(optional)"));
        AwsProviderSchemaCatalog catalog = catalogWith(List.of("aws_vpc"), evidence);
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), same(evidence))).thenReturn(safeGeneration());
        GeneratedTerraformContractInspector inspector = mock(GeneratedTerraformContractInspector.class);

        VertexAnalysisProvider provider = new VertexAnalysisProvider(
                objectReader, query -> List.of(reference), requiredProperties(), factsExtractor,
                new RetrievalQueryTextBuilder(), generationStage, catalog, inspector);

        provider.analyze(context());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> candidates = ArgumentCaptor.forClass(Collection.class);
        verify(catalog).resolve(candidates.capture());
        assertThat(candidates.getValue()).containsExactly("aws_vpc");
        verify(inspector).inspect(safeGeneration().terraformCode());
    }

    @Test
    void finalGeneratedResourceInspectionDoesNotReceiveRequestSchemaEnvelope() {
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        AnalysisGenerationResult generated = generation(
                "resource \"aws_subnet\" \"generated\" { vpc_id = \"vpc-example\" }");
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(generated);
        GeneratedTerraformContractInspector inspector = mock(GeneratedTerraformContractInspector.class);

        provider(query -> List.of(reference()), generationStage, inspector).analyze(context());

        verify(inspector).inspect(generated.terraformCode());
    }

    private VertexAnalysisProvider provider(
            ReferenceRetriever retriever,
            VertexGenerationStage generationStage
    ) {
        return provider(retriever, generationStage, mock(GeneratedTerraformContractInspector.class));
    }

    private VertexAnalysisProvider provider(
            ReferenceRetriever retriever,
            VertexGenerationStage generationStage,
            GeneratedTerraformContractInspector inspector
    ) {
        ObjectContent source = source();
        ObjectReader objectReader = mock(ObjectReader.class);
        when(objectReader.readContent(any())).thenReturn(source);
        VertexArchitectureFactsExtractor factsExtractor = mock(VertexArchitectureFactsExtractor.class);
        when(factsExtractor.extract(any())).thenReturn(new ArchitectureRetrievalFacts(
                "VPC", List.of("VPC"), List.of(), List.of("aws_vpc")));
        return new VertexAnalysisProvider(
                objectReader,
                retriever,
                requiredProperties(),
                factsExtractor,
                new RetrievalQueryTextBuilder(),
                generationStage,
                catalogWith(List.of("aws_vpc"), new AwsProviderSchemaEvidence(Map.of(
                        "aws_vpc", "cidr_block(optional)"))),
                inspector
        );
    }

    private AwsProviderSchemaCatalog catalogWith(
            List<String> supported,
            AwsProviderSchemaEvidence evidence
    ) {
        AwsProviderSchemaCatalog catalog = mock(AwsProviderSchemaCatalog.class);
        for (String resourceType : supported) {
            when(catalog.contains(resourceType)).thenReturn(true);
        }
        when(catalog.resolve(any())).thenReturn(evidence);
        return catalog;
    }

    private AnalysisRuntimeProperties requiredProperties() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        properties.setOpensearchTopK(8);
        return properties;
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
}
