package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import java.util.List;
import java.util.Objects;

public record EvaluationRunResult(
        String schemaVersion,
        String datasetVersion,
        String runId,
        ConfigurationIdentity configuration,
        List<EvaluationTrace> traces
) {
    public EvaluationRunResult {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        runId = requireText(runId, "runId");
        configuration = Objects.requireNonNull(configuration, "configuration");
        traces = traces == null ? List.of() : List.copyOf(traces);
        if (traces.isEmpty()) {
            throw new IllegalArgumentException("traces must not be empty");
        }
        for (EvaluationTrace trace : traces) {
            Objects.requireNonNull(trace, "trace");
            if (!schemaVersion.equals(trace.schemaVersion())) {
                throw new IllegalArgumentException("trace schemaVersion must match run schemaVersion");
            }
            if (!datasetVersion.equals(trace.datasetVersion())) {
                throw new IllegalArgumentException("trace datasetVersion must match run datasetVersion");
            }
            if (!runId.equals(trace.runId())) {
                throw new IllegalArgumentException("trace runId must match run runId");
            }
            if (!configuration.equals(trace.configuration())) {
                throw new IllegalArgumentException("trace configuration must match run configuration");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
