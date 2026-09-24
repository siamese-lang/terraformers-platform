package com.terraformers.modernization.analysis.vertex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VertexResponseParser {

    private final ObjectMapper objectMapper;

    public VertexResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisGenerationResult parse(
            String provider,
            String responseText,
            String stopReason,
            Integer outputTokens,
            boolean retryOccurred
    ) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            if (root == null || !root.isObject()) {
                throw new VertexResponseFormatException("Vertex response must be one JSON object");
            }

            AnalysisInputClassification classification = classification(root);
            Double confidence = confidence(root);
            requireText(root, "classificationReason");
            String summary = text(root, "summary");
            List<String> components = strings(root, "components");
            List<String> relationships = strings(root, "relationships");
            List<String> warnings = strings(root, "warnings");
            String terraformCode = text(root, "terraformCode");

            if (classification != AnalysisInputClassification.ARCHITECTURE_DIAGRAM) {
                if (!summary.isBlank() || !components.isEmpty() || !relationships.isEmpty()
                        || !warnings.isEmpty() || !terraformCode.isBlank()) {
                    throw new VertexResponseFormatException(
                            "rejected Vertex input must not include architecture or Terraform output");
                }
                throw new AnalysisInputRejectedException(classification, confidence, retryOccurred, null);
            }

            if (summary.isBlank()) {
                throw new VertexResponseFormatException("Vertex architecture response is missing summary");
            }
            if (terraformCode.isBlank()) {
                throw new VertexResponseFormatException("Vertex architecture response is missing terraformCode");
            }

            return new AnalysisGenerationResult(
                    provider,
                    classification,
                    confidence,
                    terraformCode,
                    summary,
                    components,
                    relationships,
                    warnings,
                    stopReason,
                    outputTokens,
                    retryOccurred
            );
        } catch (AnalysisInputRejectedException | VertexResponseFormatException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VertexResponseFormatException("Vertex response format is invalid", exception);
        }
    }

    private AnalysisInputClassification classification(JsonNode root) {
        String value = requireText(root, "inputType");
        try {
            return AnalysisInputClassification.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new VertexResponseFormatException("Vertex response has unknown inputType", exception);
        }
    }

    private Double confidence(JsonNode root) {
        JsonNode value = root.path("classificationConfidence");
        if (!value.isNumber() || !Double.isFinite(value.asDouble())
                || value.asDouble() < 0 || value.asDouble() > 1) {
            throw new VertexResponseFormatException("Vertex response has invalid classificationConfidence");
        }
        return value.asDouble();
    }

    private String requireText(JsonNode root, String field) {
        String value = text(root, field);
        if (value.isBlank()) {
            throw new VertexResponseFormatException("Vertex response is missing " + field);
        }
        return value;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual()) {
            throw new VertexResponseFormatException("Vertex response field " + field + " must be text");
        }
        return value.asText().strip();
    }

    private List<String> strings(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            throw new VertexResponseFormatException("Vertex response field " + field + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new VertexResponseFormatException(
                        "Vertex response field " + field + " must contain only strings");
            }
            String text = value.asText().strip();
            if (!text.isBlank()) values.add(text);
        }
        return List.copyOf(values);
    }
}
