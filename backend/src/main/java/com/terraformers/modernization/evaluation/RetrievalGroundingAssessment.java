package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationTrace.FirstDivergence;
import com.terraformers.modernization.evaluation.EvaluationTrace.ReferenceHit;
import java.util.List;
import java.util.Map;

public record RetrievalGroundingAssessment(
        String caseId,
        boolean applicable,
        EvaluationStageStatus retrievalStatus,
        String queryText,
        List<String> resourceTypeFilters,
        Integer requestedTopK,
        List<ReferenceHit> orderedHits,
        Coverage projectDecisionCoverage,
        Coverage resourceTypeCoverage,
        EvaluationStageStatus generationStatus,
        boolean retrievalToGenerationHandoffComplete,
        int requiredGeneratedResourceMatched,
        int requiredGeneratedResourceTotal,
        int forbiddenGeneratedResourceCount,
        EvaluationStageStatus validationStatus,
        Boolean terraformValidationPassed,
        FirstDivergence firstDivergence,
        boolean groundingGap,
        boolean groundingGapWithValidOutput,
        Long factExtractionLatencyMs,
        Long retrievalLatencyMs,
        Long generationLatencyMs,
        Long observedEndToEndStageSumMs
) {
    public record Coverage(
            int matched,
            int total,
            Double coverage,
            Map<String, Integer> firstMatchedRank,
            List<String> missing
    ) {}
}
