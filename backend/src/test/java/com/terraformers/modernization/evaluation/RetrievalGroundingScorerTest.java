package com.terraformers.modernization.evaluation;

import static com.terraformers.modernization.evaluation.CaseATestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalGroundingScorerTest {
    private final RetrievalGroundingScorer scorer = new RetrievalGroundingScorer();
    @Test void scoresRetainedVpcEquivalentGapExactly() {
        var definition = definition("vpc", EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("tfref-v2-sg-relations"), List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"));
        var trace = trace("r", "vpc", EvaluationStageStatus.PASS,
                List.of(hit(1, "schema-vpc", "aws_vpc"), hit(2, "schema-lb", "aws_lb")),
                List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"), true);
        var result = scorer.score(definition, trace);
        assertThat(result.projectDecisionCoverage().matched()).isZero(); assertThat(result.projectDecisionCoverage().total()).isOne();
        assertThat(result.resourceTypeCoverage().matched()).isEqualTo(2); assertThat(result.resourceTypeCoverage().total()).isEqualTo(4);
        assertThat(result.groundingGap()).isTrue(); assertThat(result.groundingGapWithValidOutput()).isTrue();
    }
    @Test void usesExactIdsAndResourceTypesAndRecordsRanks() {
        var definition = definition("x", EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("decision"), List.of("aws_db_instance", "aws_security_group"));
        var result = scorer.score(definition, trace("r", "x", EvaluationStageStatus.PASS,
                List.of(
                        hit(1, "decision-extra", "aws_db_subnet_group"),
                        hit(2, "decision", "aws_db_instance", "aws_security_group"),
                        hit(3, "official", "aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group")),
                List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"), true));
        assertThat(result.projectDecisionCoverage().firstMatchedRank()).containsEntry("decision", 2);
        assertThat(result.resourceTypeCoverage().matched()).isEqualTo(2);
        assertThat(result.projectDecisionCoverage().missing()).isEmpty();
        assertThat(result.resourceTypeCoverage().missing()).isEmpty();
        assertThat(result.retrievalToGenerationHandoffComplete()).isTrue();
        assertThat(result.factResourceOfficialEvidenceCoverage().matched()).isEqualTo(4);
        assertThat(result.generatedResourceOfficialEvidenceCoverage().matched()).isEqualTo(4);
        assertThat(result.groundingGap()).isFalse();
    }
    @Test void exposesGeneratedResourcesWithoutSelectedOfficialEvidence() {
        var definition = definition(
                "x",
                EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("decision"),
                List.of("aws_vpc"));
        var result = scorer.score(
                definition,
                trace(
                        "r",
                        "x",
                        EvaluationStageStatus.PASS,
                        List.of(
                                hit(1, "decision"),
                                hit(2, "official", "aws_vpc")),
                        List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"),
                        true));

        assertThat(result.resourceTypeCoverage().missing()).isEmpty();
        assertThat(result.factResourceOfficialEvidenceCoverage().missing())
                .containsExactlyInAnyOrder("aws_lb", "aws_db_instance", "aws_security_group");
        assertThat(result.generatedResourceOfficialEvidenceCoverage().missing())
                .containsExactlyInAnyOrder("aws_lb", "aws_db_instance", "aws_security_group");
        assertThat(result.groundingGap()).isFalse();
    }

    @Test void projectDecisionCoverageRequiresTerraformersPatternDocumentType() {
        var definition = definition(
                "x",
                EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("decision"),
                List.of("aws_vpc"));
        var providerLookalike = new EvaluationTrace.ReferenceHit(
                1,
                "decision",
                .9,
                "same id, wrong document type",
                "PROVIDER_DOCUMENTATION",
                "AWS_PROVIDER_DOC",
                "source",
                List.of("aws_vpc"),
                "5.100.0",
                "terraformers-reference-v4",
                100,
                List.of());

        var result = scorer.score(
                definition,
                trace(
                        "r",
                        "x",
                        EvaluationStageStatus.PASS,
                        List.of(providerLookalike, hit(2, "official", "aws_vpc")),
                        List.of("aws_vpc"),
                        true));

        assertThat(result.projectDecisionCoverage().matched()).isZero();
        assertThat(result.projectDecisionCoverage().missing()).containsExactly("decision");
        assertThat(result.groundingGap()).isTrue();
    }

    @Test void handlesCompleteFailureAndNonApplicable() {
        var positive = definition("x", EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM, List.of("decision"), List.of("aws_vpc"));
        assertThat(scorer.score(positive, trace("r", "x", EvaluationStageStatus.FAIL, List.of(), List.of(), false)).groundingGap()).isFalse();
        var negative = definition("n", EvaluationCase.InputClassification.AMBIGUOUS, List.of(), List.of());
        var assessed = scorer.score(negative, trace("r", "n", EvaluationStageStatus.NOT_RUN, List.of(), List.of(), false));
        assertThat(assessed.applicable()).isFalse(); assertThat(assessed.resourceTypeCoverage()).isNull();
        assertThat(assessed.groundingGap()).isFalse();
    }

    @Test void closureImprovesOnlyGeneratedCoverageWithoutRetroactivelyRepairingInitialRetrieval() {
        var definition = definition("x", EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("required-decision"), List.of("aws_vpc", "aws_instance"));
        var initial = trace("r", "x", EvaluationStageStatus.PASS, List.of(hit(1, "vpc", "aws_vpc")),
                List.of("aws_vpc", "aws_instance"), true);
        var finalHits = List.of(hit(1, "vpc", "aws_vpc"), hit(2, "instance", "aws_instance"),
                hit(3, "required-decision", "aws_instance"));
        var updated = withClosure(initial, new EvaluationTrace.GroundingClosureEvidence("first draft", true,
                new EvaluationTrace.ClosureRetrievalEvidence("generated resources", List.of("aws_instance"), 8,
                        finalHits.subList(1, 3)), finalHits, true, List.of()));
        var before = scorer.score(definition, initial);
        var after = scorer.score(definition, updated);

        assertThat(updated.retrieval()).isEqualTo(initial.retrieval());
        assertThat(updated.generation().evidence().suppliedReferenceIds()).containsExactly("vpc");
        assertThat(after.orderedHits()).isEqualTo(before.orderedHits());
        assertThat(after.resourceTypeCoverage()).isEqualTo(before.resourceTypeCoverage());
        assertThat(after.resourceTypeCoverage().missing()).containsExactly("aws_instance");
        assertThat(after.projectDecisionCoverage()).isEqualTo(before.projectDecisionCoverage());
        assertThat(after.projectDecisionCoverage().missing()).containsExactly("required-decision");
        assertThat(after.factResourceOfficialEvidenceCoverage()).isEqualTo(before.factResourceOfficialEvidenceCoverage());
        assertThat(after.retrievalToGenerationHandoffComplete()).isTrue();
        assertThat(before.generatedResourceOfficialEvidenceCoverage().matched()).isEqualTo(1);
        assertThat(after.generatedResourceOfficialEvidenceCoverage().matched()).isEqualTo(2);
        assertThat(after.groundingGap()).isTrue();
        assertThat(new CaseAQualityCalibrationScorer().score(definition, updated).labeledQualitySuccess()).isFalse();
    }

    @Test void explicitFinalSelectionDoesNotFallBackToInitialOfficialDocumentsOrCountProviderSchema() {
        var definition = definition("x", EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of(), List.of("aws_vpc"));
        var initial = trace("r", "x", EvaluationStageStatus.PASS, List.of(hit(1, "vpc", "aws_vpc")),
                List.of("aws_vpc"), true);
        var schemaOnly = new EvaluationTrace.ReferenceHit(1, "schema", 1, "schema", "PROVIDER_DOCUMENTATION",
                "AWS_PROVIDER_SCHEMA", "schema.json", List.of("aws_vpc"), "5.100.0", "v4", 1, List.of());
        for (List<EvaluationTrace.ReferenceHit> finalHits : List.of(List.of(schemaOnly), List.<EvaluationTrace.ReferenceHit>of())) {
            var updated = withClosure(initial, new EvaluationTrace.GroundingClosureEvidence("first", true,
                    new EvaluationTrace.ClosureRetrievalEvidence("query", List.of("aws_vpc"), 8, finalHits),
                    finalHits, true, List.of("aws_vpc")));
            var score = scorer.score(definition, updated);
            assertThat(score.factResourceOfficialEvidenceCoverage().matched()).isEqualTo(1);
            assertThat(score.generatedResourceOfficialEvidenceCoverage().matched()).isZero();
            assertThat(score.generatedResourceOfficialEvidenceCoverage().missing()).containsExactly("aws_vpc");
        }
    }

    @Test void historicalJsonWithoutClosureFieldsRetainsDeterministicScoringAndDoesNotInventClosure() throws Exception {
        var definition = definition("x", EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("decision"), List.of("aws_vpc"));
        var historical = trace("historical-format", "x", EvaluationStageStatus.PASS,
                List.of(hit(1, "decision"), hit(2, "vpc", "aws_vpc")), List.of("aws_vpc", "aws_instance"), true);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(historical);
        assertThat(json).doesNotContain("groundingClosure", "finalSelectedReferences", "closureAttempted");
        var parsed = mapper.readValue(json, EvaluationTrace.class);

        assertThat(parsed.generation().evidence().groundingClosure()).isNull();
        assertThat(scorer.score(definition, parsed)).isEqualTo(scorer.score(definition, historical));
        assertThat(scorer.score(definition, parsed).generatedResourceOfficialEvidenceCoverage().missing())
                .containsExactly("aws_instance");
        assertThat(new CaseAQualityCalibrationScorer().score(definition, parsed))
                .isEqualTo(new CaseAQualityCalibrationScorer().score(definition, historical));
        assertThat(mapper.readTree(mapper.writeValueAsString(parsed))).isEqualTo(mapper.readTree(json));
    }

    private EvaluationTrace withClosure(EvaluationTrace trace, EvaluationTrace.GroundingClosureEvidence closure) {
        var original = trace.generation().evidence();
        var generation = new EvaluationTrace.GenerationEvidence(original.suppliedReferenceIds(),
                original.observedClassification(), original.classificationConfidence(), original.summary(),
                original.components(), original.relationships(), original.warnings(), original.terraformCode(),
                original.generatedResourceTypes(), original.generatedModuleSources(), original.stopReason(),
                original.usage(), original.retryOccurred(), closure);
        return new EvaluationTrace(trace.schemaVersion(), trace.datasetVersion(), trace.runId(), trace.caseId(),
                trace.input(), trace.configuration(), trace.factExtraction(), trace.retrieval(),
                EvaluationTrace.StageTrace.pass(EvaluationStage.GENERATION, trace.generation().latencyMs(), generation),
                trace.validation(), trace.firstDivergence());
    }
}
