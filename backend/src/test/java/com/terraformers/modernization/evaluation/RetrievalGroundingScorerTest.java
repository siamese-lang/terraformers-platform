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
                List.of(hit(1, "decision-extra", "aws_db_subnet_group"), hit(2, "decision", "aws_db_instance", "aws_security_group")),
                List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"), true));
        assertThat(result.projectDecisionCoverage().firstMatchedRank()).containsEntry("decision", 2);
        assertThat(result.resourceTypeCoverage().matched()).isEqualTo(2);
        assertThat(result.projectDecisionCoverage().missing()).isEmpty();
        assertThat(result.resourceTypeCoverage().missing()).isEmpty();
        assertThat(result.retrievalToGenerationHandoffComplete()).isTrue();
        assertThat(result.groundingGap()).isFalse();
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
