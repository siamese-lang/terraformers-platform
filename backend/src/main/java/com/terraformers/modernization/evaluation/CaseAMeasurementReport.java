package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import java.util.List;

public record CaseAMeasurementReport(
        String reportSchemaVersion,
        String sourceRunId,
        String datasetVersion,
        ConfigurationIdentity configuration,
        int caseCount,
        List<RetrievalGroundingAssessment> cases,
        Counts counts
) {
    public static final String SCHEMA_VERSION = "case-a-retrieval-grounding-report-v1";

    public record Counts(
            int factExtractionPass,
            int factExtractionFail,
            int retrievalPass,
            int retrievalFail,
            int retrievalNotRun,
            int groundingGap,
            int groundingGapWithValidOutput,
            int negativeControlCorrect,
            int negativeControlTotal,
            int validationPass,
            int validationFail
    ) {}
}
