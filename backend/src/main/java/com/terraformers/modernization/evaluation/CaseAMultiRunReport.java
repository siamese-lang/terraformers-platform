package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import java.util.List;
import java.util.Map;

public record CaseAMultiRunReport(
        String reportSchemaVersion,
        int runCount,
        String datasetVersion,
        ConfigurationIdentity configuration,
        List<String> sourceRunIds,
        Frequency groundingGap,
        Frequency groundingGapWithValidOutput,
        Statistics factExtractionLatencyMs,
        Statistics retrievalLatencyMs,
        Statistics generationLatencyMs,
        Statistics observedEndToEndStageSumMs,
        Map<String, CaseCoverage> caseCoverage
) {
    public static final String SCHEMA_VERSION = "case-a-retrieval-grounding-multi-run-v1";
    public record Frequency(int sampleCount, int failureCount, double frequency) {}
    public record Statistics(int sampleCount, long min, double median, long max) {}
    public record CaseCoverage(List<Integer> projectDecisionMatched, List<Integer> projectDecisionTotal,
                               List<Integer> resourceTypeMatched, List<Integer> resourceTypeTotal) {}
}
