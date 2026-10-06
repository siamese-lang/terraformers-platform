package com.terraformers.modernization.evaluation;

import static com.terraformers.modernization.evaluation.CaseATestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.analysis.EvidenceQualityAssessment.QualityStatus;
import com.terraformers.modernization.evaluation.CaseAQualityCalibrationReport.RuntimeQualityComparison;
import com.terraformers.modernization.evaluation.EvaluationCase.InputClassification;
import com.terraformers.modernization.evaluation.EvaluationTrace.GenerationEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.StageTrace;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaseAQualityCalibrationTest {
    private final CaseAQualityCalibrationScorer scorer = new CaseAQualityCalibrationScorer();

    @Test void historicalVpcEquivalentIsMachineRepresentableAsFalseGreen() {
        var definition = definition("vpc", InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("tfref-v2-sg-relations"),
                List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"));
        var trace = trace("historical", "vpc", EvaluationStageStatus.PASS,
                List.of(hit(1, "schema-vpc", "aws_vpc"), hit(2, "schema-lb", "aws_lb")),
                requiredResources(), true);

        var result = scorer.score(definition, trace);

        assertThat(result.grounding().projectDecisionCoverage().matched()).isZero();
        assertThat(result.grounding().resourceTypeCoverage().matched()).isEqualTo(2);
        assertThat(result.grounding().resourceTypeCoverage().total()).isEqualTo(4);
        assertThat(result.technicalSuccess()).isTrue();
        assertThat(result.labeledQualitySuccess()).isFalse();
        assertThat(result.falseGreen()).isTrue();
    }

    @Test void technicallyFailedCaseIsNotFalseGreen() {
        var definition = positiveDefinition();
        var result = scorer.score(definition, trace("run", "positive", EvaluationStageStatus.FAIL,
                List.of(), List.of(), false));

        assertThat(result.technicalSuccess()).isFalse();
        assertThat(result.falseGreen()).isFalse();
    }

    @Test void fullyGroundedPositiveMatchesCanonicalAndHoldoutSemantics() {
        var definition = positiveDefinition();
        var result = scorer.score(definition, successfulPositiveTrace());

        assertThat(result.technicalSuccess()).isTrue();
        assertThat(result.requiredFactResourceMatched()).isEqualTo(4);
        assertThat(result.requiredFactResourceTotal()).isEqualTo(4);
        assertThat(result.forbiddenFactResourceCount()).isZero();
        assertThat(result.grounding().retrievalToGenerationHandoffComplete()).isTrue();
        assertThat(result.labeledQualitySuccess()).isTrue();
        assertThat(result.falseGreen()).isFalse();
    }

    @Test void missingRequiredFactResourceFailsFrozenLabelEvenWhenLaterStagesPass() {
        var base = successfulPositiveTrace();
        var incompleteFacts = StageTrace.pass(
                EvaluationStage.FACT_EXTRACTION,
                10,
                new EvaluationTrace.FactExtractionEvidence(
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        "facts",
                        List.of(),
                        List.of(),
                        List.of("aws_vpc", "aws_lb", "aws_db_instance")));
        var trace = new EvaluationTrace(
                base.schemaVersion(), base.datasetVersion(), base.runId(), base.caseId(),
                base.input(), base.configuration(), incompleteFacts, base.retrieval(),
                base.generation(), base.validation(), null);

        var result = scorer.score(positiveDefinition(), trace);

        assertThat(result.requiredFactResourceMatched()).isEqualTo(3);
        assertThat(result.requiredFactResourceTotal()).isEqualTo(4);
        assertThat(result.labeledQualitySuccess()).isFalse();
        assertThat(result.falseGreen()).isTrue();
    }

    @Test void frozenComponentAndRelationshipLabelsUseDeterministicTopologyMatching() {
        var baseDefinition = positiveDefinition();
        var definition = new EvaluationCase(
                baseDefinition.schemaVersion(), baseDefinition.datasetVersion(), baseDefinition.caseId(),
                baseDefinition.input(), baseDefinition.expectedClassification(),
                new EvaluationCase.TextExpectation(
                        List.of("Application Load Balancer"), List.of(), List.of("OpenSearch Serverless")),
                new EvaluationCase.TextExpectation(
                        List.of("Application Load Balancer -> RDS Database"), List.of(), List.of()),
                baseDefinition.resourceTypes(), baseDefinition.retrieval(), baseDefinition.generation(),
                baseDefinition.validation(), baseDefinition.notes());

        var baseTrace = successfulPositiveTrace();
        var matchingFacts = StageTrace.pass(
                EvaluationStage.FACT_EXTRACTION,
                10,
                new EvaluationTrace.FactExtractionEvidence(
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        "facts",
                        List.of("Application Load Balancer in public subnets"),
                        List.of("Application Load Balancer sends SQL traffic to RDS Database"),
                        List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group")));
        var matchingTrace = new EvaluationTrace(
                baseTrace.schemaVersion(), baseTrace.datasetVersion(), baseTrace.runId(), baseTrace.caseId(),
                baseTrace.input(), baseTrace.configuration(), matchingFacts, baseTrace.retrieval(),
                baseTrace.generation(), baseTrace.validation(), null);

        var matched = scorer.score(definition, matchingTrace);
        assertThat(matched.componentCoverage().requiredMatched()).isOne();
        assertThat(matched.relationshipCoverage().requiredMatched()).isOne();
        assertThat(matched.labeledQualitySuccess()).isTrue();

        var missingRelationshipFacts = StageTrace.pass(
                EvaluationStage.FACT_EXTRACTION,
                10,
                new EvaluationTrace.FactExtractionEvidence(
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        "facts",
                        List.of("Application Load Balancer"),
                        List.of(),
                        List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group")));
        var missingTrace = new EvaluationTrace(
                baseTrace.schemaVersion(), baseTrace.datasetVersion(), baseTrace.runId(), baseTrace.caseId(),
                baseTrace.input(), baseTrace.configuration(), missingRelationshipFacts, baseTrace.retrieval(),
                baseTrace.generation(), baseTrace.validation(), null);

        var missed = scorer.score(definition, missingTrace);
        assertThat(missed.relationshipCoverage().missingRequired())
                .containsExactly("Application Load Balancer -> RDS Database");
        assertThat(missed.labeledQualitySuccess()).isFalse();
        assertThat(missed.falseGreen()).isTrue();
    }

    @Test void relationshipMatcherAllowsOneDescriptiveEntityTokenToBeOmitted() {
        var baseDefinition = positiveDefinition();
        var definition = new EvaluationCase(
                baseDefinition.schemaVersion(), baseDefinition.datasetVersion(), baseDefinition.caseId(),
                baseDefinition.input(), baseDefinition.expectedClassification(),
                new EvaluationCase.TextExpectation(
                        List.of("VPC Endpoint", "OpenSearch Serverless VECTORSEARCH"), List.of(), List.of()),
                new EvaluationCase.TextExpectation(
                        List.of("VPC Endpoint -> OpenSearch Serverless VECTORSEARCH"), List.of(), List.of()),
                baseDefinition.resourceTypes(), baseDefinition.retrieval(), baseDefinition.generation(),
                baseDefinition.validation(), baseDefinition.notes());
        var base = successfulPositiveTrace();
        var facts = StageTrace.pass(
                EvaluationStage.FACT_EXTRACTION,
                10,
                new EvaluationTrace.FactExtractionEvidence(
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        "facts",
                        List.of("VPC Endpoint", "OpenSearch Serverless VECTORSEARCH Collection"),
                        List.of("VPC Endpoint connects privately to OpenSearch Serverless"),
                        List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group")));
        var trace = new EvaluationTrace(
                base.schemaVersion(), base.datasetVersion(), base.runId(), base.caseId(),
                base.input(), base.configuration(), facts, base.retrieval(),
                base.generation(), base.validation(), null);

        assertThat(scorer.score(definition, trace).relationshipCoverage().requiredMatched()).isOne();
    }

    @Test void relationshipQualifierMustBePresentWhenFrozenLabelRequiresIt() {
        var baseDefinition = positiveDefinition();
        var definition = new EvaluationCase(
                baseDefinition.schemaVersion(), baseDefinition.datasetVersion(), baseDefinition.caseId(),
                baseDefinition.input(), baseDefinition.expectedClassification(),
                new EvaluationCase.TextExpectation(
                        List.of("Application Pod", "IAM Role"), List.of(), List.of()),
                new EvaluationCase.TextExpectation(
                        List.of("Application Pod -> IAM Role through IRSA"), List.of(), List.of()),
                baseDefinition.resourceTypes(), baseDefinition.retrieval(), baseDefinition.generation(),
                baseDefinition.validation(), baseDefinition.notes());
        var base = successfulPositiveTrace();

        var goodFacts = StageTrace.pass(
                EvaluationStage.FACT_EXTRACTION,
                10,
                new EvaluationTrace.FactExtractionEvidence(
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        "facts",
                        List.of("Application Pod", "IAM Role"),
                        List.of("Application Pod assumes IAM Role via OIDC / IRSA"),
                        List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group")));
        var good = new EvaluationTrace(
                base.schemaVersion(), base.datasetVersion(), base.runId(), base.caseId(),
                base.input(), base.configuration(), goodFacts, base.retrieval(),
                base.generation(), base.validation(), null);
        assertThat(scorer.score(definition, good).relationshipCoverage().requiredMatched()).isOne();

        var badFacts = StageTrace.pass(
                EvaluationStage.FACT_EXTRACTION,
                10,
                new EvaluationTrace.FactExtractionEvidence(
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        "facts",
                        List.of("Application Pod", "IAM Role"),
                        List.of("Application Pod can reach IAM Role"),
                        List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group")));
        var bad = new EvaluationTrace(
                base.schemaVersion(), base.datasetVersion(), base.runId(), base.caseId(),
                base.input(), base.configuration(), badFacts, base.retrieval(),
                base.generation(), base.validation(), null);

        assertThat(scorer.score(definition, bad).relationshipCoverage().requiredMatched()).isZero();
    }

    @Test void missingRequiredGeneratedResourceFailsFrozenLabel() {
        var trace = trace("run", "positive", EvaluationStageStatus.PASS, completeHits(),
                List.of("aws_vpc", "aws_lb", "aws_db_instance"), true);
        assertFalseGreen(trace);
    }

    @Test void forbiddenGeneratedResourceFailsFrozenLabel() {
        var generated = new java.util.ArrayList<>(requiredResources());
        generated.add("aws_s3_bucket");
        assertFalseGreen(trace("run", "positive", EvaluationStageStatus.PASS, completeHits(), generated, true));
    }

    @Test void validationMismatchFailsFrozenLabel() {
        assertFalseGreen(trace("run", "positive", EvaluationStageStatus.PASS, completeHits(), requiredResources(), false));
    }

    @Test void expectedValidationFailureMatchesRunnerFailureTraceWithoutBecomingTechnicalSuccess() {
        var baseDefinition = positiveDefinition();
        var definition = new EvaluationCase(
                baseDefinition.schemaVersion(), baseDefinition.datasetVersion(), baseDefinition.caseId(),
                baseDefinition.input(), baseDefinition.expectedClassification(), baseDefinition.components(),
                baseDefinition.relationships(), baseDefinition.resourceTypes(), baseDefinition.retrieval(),
                baseDefinition.generation(), EvaluationCase.ValidationExpectation.FAIL, baseDefinition.notes());
        var baseTrace = successfulPositiveTrace();
        var failure = new EvaluationFailure(
                EvaluationStage.VALIDATION,
                EvaluationFailureCategory.TERRAFORM_STRUCTURAL_VALIDATION,
                "expected invalid Terraform");
        var validation = StageTrace.fail(
                EvaluationStage.VALIDATION,
                5,
                new EvaluationTrace.ValidationEvidence(
                        new EvaluationTrace.ValidationCheck("validator", false, "expected invalid Terraform"),
                        List.of()),
                failure);
        var trace = new EvaluationTrace(
                baseTrace.schemaVersion(), baseTrace.datasetVersion(), baseTrace.runId(), baseTrace.caseId(),
                baseTrace.input(), baseTrace.configuration(), baseTrace.factExtraction(), baseTrace.retrieval(),
                baseTrace.generation(), validation, new EvaluationTrace.FirstDivergence(
                        EvaluationStage.VALIDATION, EvaluationFailureCategory.TERRAFORM_STRUCTURAL_VALIDATION));

        var result = scorer.score(definition, trace);

        assertThat(result.technicalSuccess()).isFalse();
        assertThat(result.labeledQualitySuccess()).isTrue();
        assertThat(result.falseGreen()).isFalse();
    }

    @Test void generatedResourcesWithoutOfficialEvidenceFailFrozenLabel() {
        var trace = trace(
                "run",
                "positive",
                EvaluationStageStatus.PASS,
                List.of(
                        hit(1, "decision"),
                        hit(2, "official", "aws_vpc", "aws_security_group")),
                requiredResources(),
                true);

        var result = scorer.score(positiveDefinition(), trace);

        assertThat(result.grounding().resourceTypeCoverage().missing()).isEmpty();
        assertThat(result.grounding().generatedResourceOfficialEvidenceCoverage().missing())
                .containsExactlyInAnyOrder("aws_lb", "aws_db_instance");
        assertThat(result.labeledQualitySuccess()).isFalse();
        assertThat(result.falseGreen()).isTrue();
    }

    @Test void incompleteProjectDecisionGroundingFailsFrozenLabel() {
        assertFalseGreen(trace("run", "positive", EvaluationStageStatus.PASS,
                List.of(hit(1, "provider", "aws_vpc", "aws_security_group")), requiredResources(), true));
    }

    @Test void incompleteRetrievalResourceGroundingFailsFrozenLabel() {
        assertFalseGreen(trace("run", "positive", EvaluationStageStatus.PASS,
                List.of(hit(1, "decision", "aws_vpc")), requiredResources(), true));
    }

    @Test void ambiguousNegativeControlWithEmptyTerraformPassesFrozenLabel() {
        assertNegativePass(InputClassification.AMBIGUOUS);
    }

    @Test void nonArchitectureNegativeControlWithEmptyTerraformPassesFrozenLabel() {
        assertNegativePass(InputClassification.NON_ARCHITECTURE_IMAGE);
    }

    @Test void negativeControlWithTerraformIsAFalseGreen() {
        var definition = definition("negative", InputClassification.AMBIGUOUS, List.of(), List.of());
        var result = scorer.score(definition, negativeTrace(InputClassification.AMBIGUOUS, "resource {}", List.of("aws_vpc")));

        assertThat(result.technicalSuccess()).isTrue();
        assertThat(result.labeledQualitySuccess()).isFalse();
        assertThat(result.falseGreen()).isTrue();
    }

    @Test void runtimeQualityComparisonCannotChangeFrozenLabelAndMayBeAbsent() {
        var definition = positiveDefinition();
        var failedLabel = trace("run", "positive", EvaluationStageStatus.PASS, completeHits(), requiredResources(), false);

        var evidenceBacked = scorer.score(definition, failedLabel, QualityStatus.EVIDENCE_BACKED);
        var degraded = scorer.score(definition, successfulPositiveTrace(), QualityStatus.DEGRADED);
        var absent = scorer.score(definition, successfulPositiveTrace());

        assertThat(evidenceBacked.labeledQualitySuccess()).isFalse();
        assertThat(evidenceBacked.runtimeQualityComparison()).isEqualTo(RuntimeQualityComparison.MISMATCH);
        assertThat(degraded.labeledQualitySuccess()).isTrue();
        assertThat(degraded.runtimeQualityComparison()).isEqualTo(RuntimeQualityComparison.MISMATCH);
        assertThat(absent.runtimeEvidenceQualityStatus()).isNull();
        assertThat(absent.runtimeQualityComparison()).isEqualTo(RuntimeQualityComparison.UNAVAILABLE);
    }

    @Test void reporterEmbedsUnchangedMeasurementSchemaAndUsesCalibrationSchema() {
        var definition = positiveDefinition();
        var run = new EvaluationRunResult("m3-evaluation-v1", "dataset", "run", identity(),
                List.of(successfulPositiveTrace()));
        var dataset = new EvaluationDataset("m3-evaluation-v1", "dataset", "test", List.of(definition));

        var report = new CaseAQualityCalibrationReporter().create(run, dataset,
                Map.of("positive", QualityStatus.EVIDENCE_BACKED));

        assertThat(report.reportSchemaVersion()).isEqualTo("case-a-quality-calibration-v1");
        assertThat(report.measurementReport().reportSchemaVersion())
                .isEqualTo("case-a-retrieval-grounding-report-v1");
        assertThat(report.counts().technicalSuccess()).isOne();
        assertThat(report.counts().labeledQualitySuccess()).isOne();
        assertThat(report.counts().falseGreen()).isZero();
        assertThat(report.counts().architectureCases()).isOne();
    }

    private EvaluationCase positiveDefinition() {
        return definition("positive", InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("decision"), List.of("aws_vpc", "aws_security_group"));
    }

    private EvaluationTrace successfulPositiveTrace() {
        return trace("run", "positive", EvaluationStageStatus.PASS, completeHits(), requiredResources(), true);
    }

    private List<EvaluationTrace.ReferenceHit> completeHits() {
        return List.of(
                hit(1, "decision"),
                hit(2, "official", "aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group")
        );
    }

    private List<String> requiredResources() {
        return List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group");
    }

    private void assertFalseGreen(EvaluationTrace trace) {
        var result = scorer.score(positiveDefinition(), trace);
        assertThat(result.labeledQualitySuccess()).isFalse();
        assertThat(result.falseGreen()).isTrue();
    }

    private void assertNegativePass(InputClassification classification) {
        var definition = definition("negative", classification, List.of(), List.of());
        var result = scorer.score(definition, negativeTrace(classification, "", List.of()));
        assertThat(result.technicalSuccess()).isTrue();
        assertThat(result.labeledQualitySuccess()).isTrue();
        assertThat(result.falseGreen()).isFalse();
    }

    private EvaluationTrace negativeTrace(InputClassification classification, String terraform, List<String> resources) {
        var base = trace("run", "negative", EvaluationStageStatus.NOT_RUN, List.of(), List.of(), false);
        var facts = StageTrace.pass(EvaluationStage.FACT_EXTRACTION, 10,
                new EvaluationTrace.FactExtractionEvidence(classification, "negative", List.of(), List.of(), List.of()));
        var generation = StageTrace.pass(EvaluationStage.GENERATION, 10,
                new GenerationEvidence(List.of(), classification, 1.0, "negative", List.of(), List.of(), List.of(),
                        terraform, resources, List.of(), "STOP", null, false));
        return new EvaluationTrace(base.schemaVersion(), base.datasetVersion(), base.runId(), base.caseId(), base.input(),
                base.configuration(), facts, base.retrieval(), generation, StageTrace.notRun(EvaluationStage.VALIDATION), null);
    }
}
