package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.analysis.EvidenceQualityAssessment.QualityStatus;
import com.terraformers.modernization.evaluation.EvaluationCase.InputClassification;
import java.util.List;

/** Offline comparison of pipeline mechanics with the frozen evaluation labels. */
public record CaseAQualityCalibrationReport(
        String reportSchemaVersion,
        String sourceRunId,
        String datasetVersion,
        EvaluationTrace.ConfigurationIdentity configuration,
        CaseAMeasurementReport measurementReport,
        List<CaseResult> cases,
        Counts counts
) {
    public static final String SCHEMA_VERSION = "case-a-quality-calibration-v1";

    public record CaseResult(
            String caseId,
            InputClassification expectedClassification,
            InputClassification observedClassification,
            boolean architectureCase,
            boolean technicalSuccess,
            boolean labeledQualitySuccess,
            boolean falseGreen,
            RetrievalGroundingAssessment grounding,
            QualityStatus runtimeEvidenceQualityStatus,
            RuntimeQualityComparison runtimeQualityComparison
    ) {}

    public enum RuntimeQualityComparison {
        MATCH,
        MISMATCH,
        INDETERMINATE,
        UNAVAILABLE
    }

    public record Counts(
            int caseCount,
            int technicalSuccess,
            int technicalFailure,
            int labeledQualitySuccess,
            int labeledQualityFailure,
            int falseGreen,
            int architectureCases,
            int negativeControls
    ) {}
}
