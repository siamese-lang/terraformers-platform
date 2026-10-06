package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.GeneratedTerraformContractInspector;
import com.terraformers.modernization.analysis.EvidenceQualityAssessor;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.OfficialKnowledgeCoverageCatalog;
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

    @Test
    void successfulArchitectureResultCarriesEvidenceQualityAssessment() {
        VertexGenerationStage generationStage = mock(VertexGenerationStage.class);
        when(generationStage.generate(any(), any(), any(), any())).thenReturn(safeGeneration());

        var result = provider(query -> List.of(reference()), generationStage).analyze(context());

        assertThat(result.qualityAssessment()).isNotNull();
        assertThat(result.qualityAssessment().technicalStatus())
                .isEqualTo(com.terraformers.modernization.analysis.EvidenceQualityAssessment.TechnicalStatus.PASS);
    }

    @Test
    void alreadyOfficiallyEvidencedDraftPreservesOneRetrievalAndOneGeneration() {
        ClosureFixture fixture = closureFixture(List.of(official("vpc", "aws_vpc")), List.of(),
                safeGeneration().terraformCode(), null);
        var result = fixture.provider().analyze(context());
        assertThat(result.references()).containsExactly("vpc");
        verify(fixture.retriever(), times(1)).retrieve(any());
        verify(fixture.stage(), times(1)).generate(any(), any(), any(), any());
        verify(fixture.stage(), org.mockito.Mockito.never()).repair(any(), any(), any(), any());
        verify(fixture.inspector()).inspect(safeGeneration().terraformCode());
        verify(fixture.orchestrator()).generate(any(), any(), any(), eq(List.of(official("vpc", "aws_vpc"))));
    }

    @Test
    void supportResourceClosureMergesReferencesExpandsSchemaAndRepairsOnlyTerraform() {
        ReferenceDocument vpc = official("vpc", "aws_vpc");
        ReferenceDocument decision = new ReferenceDocument("decision", "project", "project intent", 1,
                "TERRAFORMERS_PATTERN", List.of("aws_vpc"), "project.md", "5.100.0", "any-corpus",
                "PROJECT_DECISION", 1, List.of());
        String first = "resource \"aws_vpc\" \"main\" {}\nresource \"aws_instance\" \"app\" { instance_class = \"wrong\" }";
        String repaired = "resource \"aws_vpc\" \"main\" {}\nresource \"aws_instance\" \"app\" { ami = var.ami_id instance_type = var.instance_type }";
        ClosureFixture fixture = closureFixture(List.of(vpc, decision),
                List.of(vpc, official("instance", "aws_instance")), first, repaired);
        var result = fixture.provider().analyze(context());
        assertThat(result.terraformCode()).isEqualTo(repaired);
        assertThat(result.explanation()).isEqualTo("VPC");
        assertThat(result.components()).containsExactly("VPC");
        assertThat(result.references()).containsExactly("vpc", "decision", "instance");
        assertThat(result.qualityAssessment().generatedResourcesWithoutSelectedEvidence()).isEmpty();
        ArgumentCaptor<ReferenceQuery> queries = ArgumentCaptor.forClass(ReferenceQuery.class);
        verify(fixture.retriever(), times(2)).retrieve(queries.capture());
        assertThat(queries.getAllValues().get(0).resourceOnly()).isFalse();
        assertThat(queries.getAllValues().get(1).resourceOnly()).isTrue();
        assertThat(queries.getAllValues().get(1).resourceTypes()).containsExactly("aws_instance");
        assertThat(queries.getAllValues().get(1).text()).doesNotContain("aws_vpc");
        ArgumentCaptor<AwsProviderSchemaEvidence> schemas = ArgumentCaptor.forClass(AwsProviderSchemaEvidence.class);
        verify(fixture.stage(), times(1)).repair(any(), eq(generation(first)), any(), schemas.capture());
        assertThat(schemas.getValue().resourceTypes()).containsExactlyInAnyOrder("aws_vpc", "aws_instance");
        assertThat(schemas.getValue().promptText()).contains("ami", "required", "instance_type", "block");
        verify(fixture.stage(), times(1)).generate(any(), any(), any(), any());
        verify(fixture.inspector()).inspect(repaired);
        ArgumentCaptor<EvidenceQualityAssessor.Input> quality = ArgumentCaptor.forClass(EvidenceQualityAssessor.Input.class);
        verify(fixture.assessor()).assess(quality.capture());
        assertThat(quality.getValue().generatedTerraform()).isEqualTo(repaired);
        assertThat(quality.getValue().selectedReferences()).extracting(ReferenceDocument::id)
                .containsExactly("vpc", "decision", "instance");
    }

    @Test
    void newUnevidencedResourceAfterRepairRemainsDegradedWithoutThirdGeneration() {
        String first = "resource \"aws_vpc\" \"main\" {}\nresource \"aws_subnet\" \"support\" {}";
        String repaired = first + "\nresource \"aws_security_group\" \"new\" {}";
        ClosureFixture fixture = closureFixture(List.of(official("vpc", "aws_vpc")),
                List.of(official("subnet", "aws_subnet")), first, repaired);
        var result = fixture.provider().analyze(context());
        assertThat(result.qualityAssessment().generatedResourcesWithoutSelectedEvidence())
                .containsExactly("aws_security_group");
        assertThat(result.qualityAssessment().reasons()).contains(
                com.terraformers.modernization.analysis.EvidenceQualityAssessment.Reason.GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE);
        verify(fixture.retriever(), times(2)).retrieve(any());
        verify(fixture.stage(), times(1)).generate(any(), any(), any(), any());
        verify(fixture.stage(), times(1)).repair(any(), any(), any(), any());
        verify(fixture.inspector()).inspect(repaired);
    }

    @Test
    void successfulCompactFallbackStillAllowsOneClosureAndRepairWithoutAnotherCycle() throws Exception {
        String first = "resource \"aws_vpc\" \"main\" {}\nresource \"aws_subnet\" \"support\" {}";
        String repaired = first + "\nresource \"aws_security_group\" \"new\" {}";
        VertexGenerationStage stage = respondingStage(
                response(FinishReason.Known.MAX_TOKENS, ""),
                architectureResponse(first),
                response(FinishReason.Known.STOP, new ObjectMapper().writeValueAsString(Map.of("terraformCode", repaired))));
        ClosureFixture fixture = closureFixture(List.of(official("vpc", "aws_vpc")),
                List.of(official("subnet", "aws_subnet")), stage);

        var result = fixture.provider().analyze(context());

        assertThat(result.terraformCode()).isEqualTo(repaired);
        assertThat(result.explanation()).isEqualTo("VPC");
        assertThat(result.qualityAssessment().generatedResourcesWithoutSelectedEvidence())
                .containsExactly("aws_security_group");
        assertThat(result.qualityAssessment().reasons()).contains(
                com.terraformers.modernization.analysis.EvidenceQualityAssessment.Reason.GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE);
        ArgumentCaptor<Content> requests = ArgumentCaptor.forClass(Content.class);
        verify(stage, times(3)).request(requests.capture(), any());
        assertThat(requests.getAllValues().get(0).parts().orElseThrow().get(1).text()).get()
                .asString().contains("Standard mode:");
        assertThat(requests.getAllValues().get(1).parts().orElseThrow().get(1).text()).get()
                .asString().contains("Compact mode:");
        assertThat(requests.getAllValues().get(2).parts().orElseThrow()).hasSize(1);
        assertThat(requests.getAllValues().get(2).parts().orElseThrow().get(0).inlineData()).isEmpty();
        ArgumentCaptor<AnalysisGenerationResult> draft = ArgumentCaptor.forClass(AnalysisGenerationResult.class);
        verify(stage, times(1)).generate(any(), any(), any(), any());
        verify(stage, times(1)).repair(any(), draft.capture(), any(), any());
        assertThat(draft.getValue().retryOccurred()).isTrue();
        assertThat(draft.getValue().terraformCode()).isEqualTo(first);
        verify(fixture.retriever(), times(2)).retrieve(any());
        verify(fixture.inspector()).inspect(repaired);
    }

    @Test
    void repairTruncationAfterCompactFallbackNeverRetriesOrStartsAnotherClosure() throws Exception {
        String first = "resource \"aws_vpc\" \"main\" {}\nresource \"aws_subnet\" \"support\" {}";
        VertexGenerationStage stage = respondingStage(
                response(FinishReason.Known.MAX_TOKENS, ""),
                architectureResponse(first),
                response(FinishReason.Known.MAX_TOKENS, ""));
        ClosureFixture fixture = closureFixture(List.of(official("vpc", "aws_vpc")),
                List.of(official("subnet", "aws_subnet")), stage);

        assertThatThrownBy(() -> fixture.provider().analyze(context()))
                .isInstanceOfSatisfying(AnalysisProviderFailureException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(AnalysisProviderFailureReason.OUTPUT_TRUNCATED))
                .hasCauseInstanceOf(VertexOutputTruncatedException.class);

        verify(stage, times(3)).request(any(), any());
        verify(stage, times(1)).generate(any(), any(), any(), any());
        verify(stage, times(1)).repair(any(), any(), any(), any());
        verify(fixture.retriever(), times(2)).retrieve(any());
    }

    @Test
    void unavailableClosureDocumentsStillLeaveGapVisibleAfterOneRepair() {
        ClosureFixture fixture = closureFixture(List.of(reference()), List.of(),
                safeGeneration().terraformCode(), safeGeneration().terraformCode());
        var result = fixture.provider().analyze(context());
        assertThat(result.qualityAssessment().missingSelectedEvidenceResourceTypes()).containsExactly("aws_vpc");
        assertThat(result.qualityAssessment().generatedResourcesWithoutSelectedEvidence()).containsExactly("aws_vpc");
        verify(fixture.retriever(), times(2)).retrieve(any());
        verify(fixture.stage(), times(1)).repair(any(), any(), any(), any());
    }

    @Test
    void finalContractInspectionRejectsModuleIntroducedByRepair() {
        ClosureFixture fixture = closureFixture(List.of(reference()), List.of(official("vpc", "aws_vpc")),
                safeGeneration().terraformCode(), "module \"unrelated\" { source = \"remote\" }");
        assertThatThrownBy(() -> fixture.provider().analyze(context()))
                .isInstanceOf(com.terraformers.modernization.analysis.GeneratedTerraformContractViolation.class);
        verify(fixture.inspector()).inspect("module \"unrelated\" { source = \"remote\" }");
        verify(fixture.stage(), times(1)).repair(any(), any(), any(), any());
    }

    @Test
    void nonArchitectureAndAmbiguousResultsNeverEnterClosureOrRepair() {
        for (var classification : List.of(AnalysisInputClassification.NON_ARCHITECTURE_IMAGE,
                AnalysisInputClassification.AMBIGUOUS)) {
            ClosureFixture fixture = closureFixture(List.of(reference()), List.of(), "", null);
            when(fixture.stage().generate(any(), any(), any(), any())).thenReturn(new AnalysisGenerationResult(
                    "vertex:test", classification, 0.9, "", "", List.of(), List.of(), List.of(), "STOP", 1, false));
            assertThatThrownBy(() -> fixture.provider().analyze(context())).isInstanceOf(AnalysisProviderFailureException.class);
            verify(fixture.retriever(), times(1)).retrieve(any());
            verify(fixture.stage(), org.mockito.Mockito.never()).repair(any(), any(), any(), any());
        }
    }

    private ClosureFixture closureFixture(List<ReferenceDocument> initial, List<ReferenceDocument> closure,
            String first, String repaired) {
        var stage = mock(VertexGenerationStage.class);
        when(stage.generate(any(), any(), any(), any())).thenReturn(generation(first));
        when(stage.repair(any(), any(), any(), any())).thenReturn(repaired);
        return closureFixture(initial, closure, stage);
    }

    private ClosureFixture closureFixture(List<ReferenceDocument> initial, List<ReferenceDocument> closure,
            VertexGenerationStage stage) {
        ObjectReader reader = mock(ObjectReader.class);
        when(reader.readContent(any())).thenReturn(source());
        var facts = mock(VertexArchitectureFactsExtractor.class);
        when(facts.extract(any())).thenReturn(new ArchitectureRetrievalFacts("VPC", List.of("VPC"),
                List.of("VPC -> app"), List.of("aws_vpc")));
        ReferenceRetriever retriever = mock(ReferenceRetriever.class);
        when(retriever.retrieve(any())).thenReturn(initial, closure);
        var catalog = mock(AwsProviderSchemaCatalog.class);
        for (String type : List.of("aws_vpc", "aws_instance", "aws_subnet", "aws_security_group")) {
            when(catalog.contains(type)).thenReturn(true);
        }
        when(catalog.resolve(any())).thenAnswer(invocation -> {
            Collection<String> types = invocation.getArgument(0);
            Map<String, String> summaries = new java.util.LinkedHashMap<>();
            types.forEach(type -> summaries.put(type, type.equals("aws_instance")
                    ? "ami: string (required), instance_type: string (optional), root_block_device block(optional)"
                    : "arguments"));
            return new AwsProviderSchemaEvidence(summaries);
        });
        var inspector = org.mockito.Mockito.spy(new GeneratedTerraformContractInspector(catalog));
        var assessor = org.mockito.Mockito.spy(new EvidenceQualityAssessor(catalog, inspector));
        var properties = requiredProperties();
        var orchestrator = org.mockito.Mockito.spy(new VertexGroundedGenerationOrchestrator(
                stage, retriever, properties, catalog, inspector));
        var provider = new VertexAnalysisProvider(reader, retriever, properties, facts,
                new RetrievalQueryTextBuilder(), orchestrator, catalog, assessor, availableCoverage());
        return new ClosureFixture(provider, retriever, stage, inspector, assessor, orchestrator);
    }

    private VertexGenerationStage respondingStage(GenerateContentResponse... responses) {
        var stage = spy(new VertexGenerationStage(null, new VertexRuntimeProperties(),
                new VertexPromptBuilder(), new VertexResponseParser(new ObjectMapper())));
        var sequence = List.of(responses).iterator();
        doAnswer(invocation -> sequence.next()).when(stage).request(any(), any());
        return stage;
    }

    private GenerateContentResponse architectureResponse(String terraform) throws Exception {
        return response(FinishReason.Known.STOP, new ObjectMapper().writeValueAsString(Map.of(
                "inputType", "ARCHITECTURE_DIAGRAM", "classificationConfidence", 1.0,
                "classificationReason", "connected system", "summary", "VPC",
                "components", List.of("VPC"), "relationships", List.of("VPC -> app"),
                "warnings", List.of(), "terraformCode", terraform)));
    }

    private GenerateContentResponse response(FinishReason.Known reason, String text) {
        return GenerateContentResponse.builder().candidates(Candidate.builder().finishReason(reason)
                .content(Content.fromParts(Part.fromText(text)))).build();
    }

    private ReferenceDocument official(String id, String resource) {
        return new ReferenceDocument(id, id, "official evidence", 1, "AWS_PROVIDER_DOC", List.of(resource),
                "official.md", "5.100.0", "any-corpus", "PROVIDER_DOCUMENTATION", 1, List.of());
    }

    private record ClosureFixture(VertexAnalysisProvider provider, ReferenceRetriever retriever,
            VertexGenerationStage stage, GeneratedTerraformContractInspector inspector, EvidenceQualityAssessor assessor,
            VertexGroundedGenerationOrchestrator orchestrator) {}

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
        AwsProviderSchemaCatalog catalog = catalogWith(List.of("aws_vpc"), new AwsProviderSchemaEvidence(Map.of(
                "aws_vpc", "cidr_block(optional)")));
        return new VertexAnalysisProvider(
                objectReader,
                retriever,
                requiredProperties(),
                factsExtractor,
                new RetrievalQueryTextBuilder(),
                generationStage,
                catalog,
                inspector,
                new EvidenceQualityAssessor(catalog, new GeneratedTerraformContractInspector(catalog)),
                availableCoverage()
        );
    }

    private OfficialKnowledgeCoverageCatalog availableCoverage() {
        OfficialKnowledgeCoverageCatalog coverage = mock(OfficialKnowledgeCoverageCatalog.class);
        when(coverage.availableFor(any(), any())).thenReturn(java.util.Set.of("aws_vpc"));
        return coverage;
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
