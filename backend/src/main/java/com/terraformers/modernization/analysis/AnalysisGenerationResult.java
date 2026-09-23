package com.terraformers.modernization.analysis;

import java.util.List;
import java.util.Objects;

public record AnalysisGenerationResult(
        String provider,
        AnalysisInputClassification inputClassification,
        Double classificationConfidence,
        String terraformCode,
        String summary,
        List<String> components,
        List<String> relationships,
        List<String> warnings,
        String stopReason,
        Integer outputTokens,
        boolean retryOccurred
) {
    public AnalysisGenerationResult {
        provider = requireText(provider, "provider");
        inputClassification = Objects.requireNonNull(inputClassification, "inputClassification");
        if (classificationConfidence != null
                && (!Double.isFinite(classificationConfidence)
                || classificationConfidence < 0
                || classificationConfidence > 1)) {
            throw new IllegalArgumentException("classificationConfidence must be between 0 and 1");
        }
        terraformCode = terraformCode == null ? "" : terraformCode;
        summary = summary == null ? "" : summary.strip();
        components = immutable(components);
        relationships = immutable(relationships);
        warnings = immutable(warnings);
        stopReason = stopReason == null ? "" : stopReason.strip();
        if (outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("outputTokens must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
