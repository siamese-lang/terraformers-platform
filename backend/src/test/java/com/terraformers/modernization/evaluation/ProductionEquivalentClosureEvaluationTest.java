package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.GeneratedTerraformContractInspector;
import com.terraformers.modernization.analysis.TerraformCliValidator;
import com.terraformers.modernization.analysis.TerraformDraftValidation;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.analysis.vertex.VertexGenerationStage;
import com.terraformers.modernization.analysis.vertex.VertexGroundedGenerationOrchestrator;
import com.terraformers.modernization.analysis.vertex.VertexOutputTruncatedException;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationCase;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProductionEquivalentClosureEvaluationTest {
    private static final String BUCKET = "resource \"aws_s3_bucket\" \"main\" {}";
    private static final String FIRST = BUCKET + "\nresource \"aws_instance\" \"app\" { instance_class = \"t3.micro\" }";
    private static final String REPAIRED = BUCKET + "\n" + """
            resource "aws_instance" "app" {
              ami = "ami-example"
              instance_type = "t3.micro"
            }
            """.strip();
    private final ReferenceDocument bucket = official("bucket", "aws_s3_bucket");
    private final ReferenceDocument instance = official("instance", "aws_instance");
    private final ReferenceDocument decision = new ReferenceDocument("decision", "project", "project intent", 1,
            "TERRAFORMERS_PATTERN", List.of("aws_s3_bucket"), "project.md", "5.100.0", "terraformers-reference-v4",
            "PROJECT_DECISION", 1, List.of());

    @Test
    void supportedDraftUsesSharedPathWithoutClosureAndKeepsInitialReferences() {
        Fixture fixture = fixture(List.of(bucket, decision), List.of(), BUCKET, null);
        EvaluationTrace trace = fixture.runner().run(dataset(positive()), "supported").traces().get(0);

        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.generation().evidence().groundingClosure().closureAttempted()).isFalse();
        assertThat(trace.generation().evidence().groundingClosure().repairAttempted()).isFalse();
        assertThat(trace.generation().evidence().groundingClosure().finalSelectedReferences())
                .isEqualTo(trace.retrieval().evidence().hits());
        verify(fixture.retriever(), times(1)).retrieve(any());
        verify(fixture.stage(), times(1)).generate(any(), any(), any(), any());
        verify(fixture.stage(), never()).repair(any(), any(), any(), any());
        verify(fixture.orchestrator(), times(1)).generate(any(), any(), any(), eq(List.of(bucket, decision)), any());
        verify(fixture.cli()).validate(BUCKET);
    }

    @Test
    void supportResourceIsRepairedBeforeFinalTerraformReachesExecutableValidator() {
        Fixture fixture = fixture(List.of(bucket, decision), List.of(bucket, instance), FIRST, REPAIRED);
        EvaluationTrace trace = fixture.runner().run(dataset(positive()), "closure").traces().get(0);

        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.validation().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.validation().evidence().applicationValidator().name())
                .isEqualTo("ProductionEquivalentTerraformValidator");
        assertThat(trace.retrieval().evidence().hits()).extracting(EvaluationTrace.ReferenceHit::documentId)
                .containsExactly("bucket", "decision");
        assertThat(trace.generation().evidence().suppliedReferenceIds()).containsExactly("bucket", "decision");
        var closure = trace.generation().evidence().groundingClosure();
        assertThat(closure.firstDraftTerraform()).isEqualTo(FIRST).contains("instance_class");
        assertThat(closure.closureAttempted()).isTrue();
        assertThat(closure.repairAttempted()).isTrue();
        assertThat(closure.closureRetrieval().resourceTypeFilters()).containsExactly("aws_instance");
        assertThat(closure.closureRetrieval().queryText()).doesNotContain("aws_s3_bucket");
        assertThat(closure.closureRetrieval().requestedEvidenceBudget()).isEqualTo(16);
        assertThat(closure.closureRetrieval().hits()).extracting(EvaluationTrace.ReferenceHit::documentId)
                .containsExactly("bucket", "instance");
        assertThat(closure.finalSelectedReferences()).extracting(EvaluationTrace.ReferenceHit::documentId)
                .containsExactly("bucket", "decision", "instance");
        assertThat(closure.finalGeneratedResourceEvidenceGaps()).isEmpty();
        assertThat(trace.generation().evidence().terraformCode()).isEqualTo(REPAIRED).doesNotContain("instance_class");
        verify(fixture.cli(), times(1)).validate(REPAIRED);
        verify(fixture.cli(), never()).validate(FIRST);
        verify(fixture.retriever(), times(2)).retrieve(any());
        verify(fixture.orchestrator(), times(1)).generate(any(), any(), any(), any(), any());
        ArgumentCaptor<ReferenceQuery> queries = ArgumentCaptor.forClass(ReferenceQuery.class);
        verify(fixture.retriever(), times(2)).retrieve(queries.capture());
        assertThat(queries.getAllValues().get(1).resourceOnly()).isTrue();
        ArgumentCaptor<AwsProviderSchemaEvidence> schemas = ArgumentCaptor.forClass(AwsProviderSchemaEvidence.class);
        verify(fixture.stage(), times(1)).repair(any(), any(), any(), schemas.capture());
        assertThat(schemas.getValue().resourceTypes()).containsExactlyInAnyOrder("aws_s3_bucket", "aws_instance");
        assertThat(schemas.getValue().promptText()).contains("ami", "instance_type");
        var score = new RetrievalGroundingScorer().score(positive(), trace);
        assertThat(score.resourceTypeCoverage().matched()).isEqualTo(1);
        assertThat(score.projectDecisionCoverage().matched()).isEqualTo(1);
        assertThat(score.factResourceOfficialEvidenceCoverage().matched()).isEqualTo(1);
        assertThat(score.generatedResourceOfficialEvidenceCoverage().matched()).isEqualTo(2);
        assertThat(score.retrievalToGenerationHandoffComplete()).isTrue();
    }

    @Test
    void resourceIntroducedByRepairRemainsUnsupportedWithoutAnotherClosureOrRepair() {
        String finalTerraform = REPAIRED + "\nresource \"aws_security_group\" \"new\" {}";
        Fixture fixture = fixture(List.of(bucket, decision), List.of(instance), FIRST, finalTerraform);
        EvaluationTrace trace = fixture.runner().run(dataset(positive()), "remaining-gap").traces().get(0);

        assertThat(trace.generation().evidence().groundingClosure().finalGeneratedResourceEvidenceGaps())
                .containsExactly("aws_security_group");
        assertThat(new RetrievalGroundingScorer().score(positive(), trace)
                .generatedResourceOfficialEvidenceCoverage().missing()).containsExactly("aws_security_group");
        assertThat(new CaseAQualityCalibrationScorer().score(positive(), trace).labeledQualitySuccess()).isFalse();
        verify(fixture.retriever(), times(2)).retrieve(any());
        verify(fixture.stage(), times(1)).generate(any(), any(), any(), any());
        verify(fixture.stage(), times(1)).repair(any(), any(), any(), any());
        verify(fixture.cli()).validate(finalTerraform);
    }

    @Test
    void repairTruncationRetainsAttemptEvidenceAndNeverRetriesOrValidatesFirstDraft() {
        Fixture fixture = fixture(List.of(bucket, decision), List.of(instance), FIRST, REPAIRED);
        when(fixture.stage().repair(any(), any(), any(), any())).thenThrow(new VertexOutputTruncatedException(8192));
        EvaluationTrace trace = fixture.runner().run(dataset(positive()), "repair-truncated").traces().get(0);

        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.firstDivergence().category()).isEqualTo(EvaluationFailureCategory.OUTPUT_TRUNCATED);
        assertThat(trace.generation().evidence().groundingClosure().closureAttempted()).isTrue();
        assertThat(trace.generation().evidence().groundingClosure().repairAttempted()).isTrue();
        assertThat(trace.validation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
        verify(fixture.retriever(), times(2)).retrieve(any());
        verify(fixture.stage(), times(1)).repair(any(), any(), any(), any());
        verify(fixture.cli(), never()).validate(any());
    }

    @Test
    void requiredEmptyInitialRetrievalStillFailsBeforeClosureDespiteAvailableClosureDocuments() {
        Fixture fixture = fixture(List.of(), List.of(instance), FIRST, REPAIRED);
        EvaluationTrace trace = fixture.runner().run(dataset(positive()), "empty-initial").traces().get(0);

        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.firstDivergence().category()).isEqualTo(EvaluationFailureCategory.RETRIEVAL_EMPTY);
        assertThat(trace.generation().evidence().groundingClosure().closureAttempted()).isFalse();
        verify(fixture.retriever(), times(1)).retrieve(any());
        verify(fixture.stage(), never()).repair(any(), any(), any(), any());
        verify(fixture.cli(), never()).validate(any());
    }

    @Test
    void negativeAndAmbiguousCasesNeverCloseRepairOrProduceTerraform() {
        for (AnalysisInputClassification classification : List.of(AnalysisInputClassification.AMBIGUOUS,
                AnalysisInputClassification.NON_ARCHITECTURE_IMAGE)) {
            Fixture fixture = fixture(List.of(bucket), List.of(instance), BUCKET, null);
            when(fixture.stage().generate(any(), any(), any(), any())).thenThrow(
                    new AnalysisInputRejectedException(classification, .9, false, null));
            EvaluationCase definition = CaseATestFixtures.definition("negative",
                    EvaluationCase.InputClassification.valueOf(classification.name()), List.of(), List.of());
            EvaluationTrace trace = fixture.runner().run(dataset(definition), "negative").traces().get(0);

            assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.PASS);
            assertThat(trace.generation().evidence().terraformCode()).isEmpty();
            assertThat(trace.generation().evidence().groundingClosure().closureAttempted()).isFalse();
            assertThat(trace.generation().evidence().groundingClosure().repairAttempted()).isFalse();
            assertThat(trace.validation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
            verify(fixture.retriever(), times(1)).retrieve(any());
            verify(fixture.stage(), never()).repair(any(), any(), any(), any());
            verify(fixture.cli(), never()).validate(any());
        }
    }

    private Fixture fixture(List<ReferenceDocument> initial, List<ReferenceDocument> closure,
            String first, String repaired) {
        ReferenceRetriever retriever = mock(ReferenceRetriever.class);
        when(retriever.retrieve(any())).thenReturn(initial, closure);
        VertexGenerationStage stage = mock(VertexGenerationStage.class);
        when(stage.generate(any(), any(), any(), any())).thenReturn(new AnalysisGenerationResult("vertex:test",
                AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0, first, "Bucket", List.of("Bucket"),
                List.of("Bucket -> app"), List.of(), "STOP", 10, false));
        when(stage.repair(any(), any(), any(), any())).thenReturn(repaired);
        AwsProviderSchemaCatalog catalog = mock(AwsProviderSchemaCatalog.class);
        for (String type : List.of("aws_s3_bucket", "aws_instance", "aws_security_group")) {
            when(catalog.contains(type)).thenReturn(true);
        }
        when(catalog.resolve(any())).thenAnswer(invocation -> {
            Collection<String> types = invocation.getArgument(0);
            Map<String, String> summaries = new LinkedHashMap<>();
            types.forEach(type -> summaries.put(type, type.equals("aws_instance")
                    ? "ami: string (optional), instance_type: string (optional)" : "arguments"));
            return new AwsProviderSchemaEvidence(summaries);
        });
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        GeneratedTerraformContractInspector inspector = new GeneratedTerraformContractInspector(catalog);
        var orchestrator = spy(new VertexGroundedGenerationOrchestrator(stage, retriever, properties, catalog, inspector));
        TerraformCliValidator cli = mock(TerraformCliValidator.class);
        String finalTerraform = repaired == null ? first : repaired;
        when(cli.validate(finalTerraform)).thenReturn(new TerraformDraftValidation(true, finalTerraform, null));
        var identity = new EvaluationTrace.ConfigurationIdentity("terraformers-reference-v4", "5.100.0",
                "vertex", "vertex", "REQUIRED", 8, "gemini-3.8-flash", "gemini-embedding-2", "test");
        EvaluationRunner runner = LiveEvaluationLauncher.productionEquivalentRunner(
                source -> new ArchitectureRetrievalFacts("Bucket", List.of("Bucket"), List.of("Bucket -> app"),
                        List.of("aws_s3_bucket")),
                new RetrievalQueryTextBuilder(), retriever, orchestrator, new TerraformDraftValidator(),
                inspector, cli, RetrievalMode.REQUIRED, identity);
        return new Fixture(runner, retriever, stage, orchestrator, cli);
    }

    private EvaluationCase positive() {
        var types = new EvaluationCase.TextExpectation(List.of("aws_s3_bucket"), List.of(), List.of());
        return new EvaluationCase("m3-evaluation-v1", "test", "support", new EvaluationCase.InputFixture(
                "fixture.png", "hash", "image/png"), EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                EvaluationCase.TextExpectation.empty(), EvaluationCase.TextExpectation.empty(), types,
                new EvaluationCase.RetrievalExpectation(List.of(), List.of(), List.of("aws_s3_bucket"),
                        List.of("decision"), List.of()),
                new EvaluationCase.GenerationExpectation(true, types), EvaluationCase.ValidationExpectation.PASS, List.of());
    }

    private LoadedEvaluationDataset dataset(EvaluationCase definition) {
        return new LoadedEvaluationDataset(new EvaluationDataset(definition.schemaVersion(), definition.datasetVersion(),
                "test", List.of(definition)),
                List.of(new LoadedEvaluationCase(definition, Path.of("fixture.png"), new byte[] {1, 2, 3})));
    }

    private ReferenceDocument official(String id, String type) {
        return new ReferenceDocument(id, id, "official evidence", 1, "AWS_PROVIDER_DOC", List.of(type),
                "official.md", "5.100.0", "terraformers-reference-v4", "PROVIDER_DOCUMENTATION", 1, List.of());
    }

    private record Fixture(EvaluationRunner runner, ReferenceRetriever retriever, VertexGenerationStage stage,
            VertexGroundedGenerationOrchestrator orchestrator, TerraformCliValidator cli) {}
}
