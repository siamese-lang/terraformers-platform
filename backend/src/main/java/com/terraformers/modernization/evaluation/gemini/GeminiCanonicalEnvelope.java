package com.terraformers.modernization.evaluation.gemini;

import com.terraformers.modernization.analysis.AnalysisInputClassification;
import java.util.List;

record GeminiCanonicalEnvelope(
        AnalysisInputClassification inputType,
        double classificationConfidence,
        String classificationReason,
        String summary,
        List<String> components,
        List<String> relationships,
        List<String> resourceTypes
) {
    GeminiCanonicalEnvelope {
        if (inputType == null) throw new IllegalArgumentException("canonical inputType is required");
        if (!Double.isFinite(classificationConfidence)
                || classificationConfidence < 0.0
                || classificationConfidence > 1.0) {
            throw new IllegalArgumentException("canonical classificationConfidence must be between 0 and 1");
        }
        classificationReason = classificationReason == null ? "" : classificationReason.strip();
        summary = summary == null ? "" : summary.strip();
        components = copy(components);
        relationships = copy(relationships);
        resourceTypes = copy(resourceTypes);
        if (classificationReason.isBlank()) {
            throw new IllegalArgumentException("canonical classificationReason is required");
        }
        if (inputType != AnalysisInputClassification.ARCHITECTURE_DIAGRAM
                && (!summary.isBlank() || !components.isEmpty()
                || !relationships.isEmpty() || !resourceTypes.isEmpty())) {
            throw new IllegalArgumentException(
                    "non-architecture canonical envelope must not contain architecture facts");
        }
        if (inputType == AnalysisInputClassification.ARCHITECTURE_DIAGRAM && summary.isBlank()) {
            throw new IllegalArgumentException("architecture canonical envelope requires summary");
        }
    }

    private static List<String> copy(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }
}
