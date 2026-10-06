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
}
