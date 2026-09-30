package com.terraformers.modernization.evaluation.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class GeminiCanonicalEnvelopeExtractor {

    static final int MAX_OUTPUT_TOKENS = 1200;
    static final int MAX_LIST_ITEMS = 16;
    static final ThinkingLevel.Known THINKING_LEVEL = ThinkingLevel.Known.LOW;
    private static final Pattern RESOURCE_TYPE = Pattern.compile("aws_[a-z0-9_]+");
    static final String PROMPT = """
            Analyze the image once and return one compact JSON object matching the supplied schema.

            Classification rules:
            - inputType must be exactly ARCHITECTURE_DIAGRAM, NON_ARCHITECTURE_IMAGE, or AMBIGUOUS.
            - ARCHITECTURE_DIAGRAM requires deployable system components and at least one identifiable connection,
              flow, dependency, containment, network boundary, or tier relationship.
            - Accept cloud, on-premises, WEB/WAS/DB, Kubernetes, API/message-flow, hand-drawn, and ordinary
              boxes-and-arrows architecture diagrams.
            - Classify photos, logos, isolated icons, memes, posters, banners, application/console UI screenshots,
              documents, tables, receipts, unrelated charts, and unconnected cloud-icon collections as
              NON_ARCHITECTURE_IMAGE.
            - Use AMBIGUOUS when system meaning or relationships cannot be determined, labels are insufficient,
              or the diagram is cropped.

            Canonical fact rules:
            - For NON_ARCHITECTURE_IMAGE or AMBIGUOUS, summary must be empty and components, relationships,
              and resourceTypes must all be empty arrays.
            - For ARCHITECTURE_DIAGRAM, summarize the visible system in at most 320 characters.
            - Keep components to at most 16 concise visible components.
            - Keep relationships to at most 16 concise visible relationships.
            - Keep resourceTypes to at most 16 Terraform AWS provider identifiers matching aws_[a-z0-9_]+.
            - Do not infer credentials, account IDs, ARNs, secrets, or resources that are not supported by the image.
            - Do not generate Terraform, Markdown, or explanatory prose outside the JSON object.
            """;

    private final EnvelopeClient client;
    private final ObjectMapper objectMapper;
    private final String modelId;

    GeminiCanonicalEnvelopeExtractor(Client client, ObjectMapper objectMapper, String modelId) {
        this((id, content, config) -> {
            GenerateContentResponse response = client.models.generateContent(id, content, config);
            FinishReason reason = response.finishReason();
            return new EnvelopeResponse(
                    response.text(),
                    reason != null && reason.knownEnum() == FinishReason.Known.MAX_TOKENS
            );
        }, objectMapper, modelId);
    }

    GeminiCanonicalEnvelopeExtractor(EnvelopeClient client, ObjectMapper objectMapper, String modelId) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.modelId = modelId;
    }

    GeminiCanonicalEnvelope extract(ObjectContent source) {
        EnvelopeResponse response = client.generate(
                modelId,
                Content.fromParts(
                        Part.fromBytes(source.bytes(), source.metadata().contentType()),
                        Part.fromText(PROMPT)
                ),
                config()
        );
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw new IllegalStateException("canonical envelope response is empty");
        }
        if (response.truncated()) {
            throw new IllegalStateException("canonical envelope response reached max output tokens");
        }
        try {
            JsonNode root = objectMapper.readTree(response.text());
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("canonical envelope must be one JSON object");
            }
            AnalysisInputClassification inputType = AnalysisInputClassification.valueOf(text(root, "inputType"));
            double confidence = number(root, "classificationConfidence");
            String reason = text(root, "classificationReason");
            String summary = textAllowEmpty(root, "summary");
            List<String> components = strings(root, "components", false);
            List<String> relationships = strings(root, "relationships", false);
            List<String> resources = strings(root, "resourceTypes", true);
            return new GeminiCanonicalEnvelope(
                    inputType, confidence, reason, summary, components, relationships, resources);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("canonical envelope response is invalid", exception);
        }
    }

    GenerateContentConfig config() {
        return GenerateContentConfig.builder()
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel(THINKING_LEVEL))
                .responseMimeType("application/json")
                .responseJsonSchema(schema())
                .build();
    }

    private Map<String, Object> schema() {
        Map<String, Object> boundedStringArray = Map.of(
                "type", "array",
                "maxItems", MAX_LIST_ITEMS,
                "items", Map.of("type", "string", "maxLength", 100)
        );
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "inputType", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "ARCHITECTURE_DIAGRAM",
                                        "NON_ARCHITECTURE_IMAGE",
                                        "AMBIGUOUS")),
                        "classificationConfidence", Map.of(
                                "type", "number", "minimum", 0, "maximum", 1),
                        "classificationReason", Map.of(
                                "type", "string", "minLength", 1, "maxLength", 240),
                        "summary", Map.of(
                                "type", "string", "maxLength", 320),
                        "components", boundedStringArray,
                        "relationships", boundedStringArray,
                        "resourceTypes", boundedStringArray
                ),
                "required", List.of(
                        "inputType", "classificationConfidence", "classificationReason",
                        "summary", "components", "relationships", "resourceTypes"),
                "additionalProperties", false
        );
    }

    private String text(JsonNode root, String field) {
        String value = textAllowEmpty(root, field);
        if (value.isBlank()) throw new IllegalStateException("canonical field " + field + " must not be blank");
        return value;
    }

    private String textAllowEmpty(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual()) throw new IllegalStateException("canonical field " + field + " must be text");
        return value.asText().strip();
    }

    private double number(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isNumber()) throw new IllegalStateException("canonical field " + field + " must be numeric");
        double result = value.asDouble();
        if (!Double.isFinite(result) || result < 0 || result > 1) {
            throw new IllegalStateException("canonical confidence must be between 0 and 1");
        }
        return result;
    }

    private List<String> strings(JsonNode root, String field, boolean resourceTypes) {
        JsonNode node = root.path(field);
        if (!node.isArray()) throw new IllegalStateException("canonical field " + field + " must be an array");
        List<String> out = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new IllegalStateException("canonical arrays must contain strings");
            String text = value.asText().strip();
            if (text.isBlank()) continue;
            if (resourceTypes && !RESOURCE_TYPE.matcher(text).matches()) {
                throw new IllegalStateException("canonical resource type is invalid");
            }
            if (out.size() < MAX_LIST_ITEMS) out.add(text);
        }
        return List.copyOf(out);
    }

    @FunctionalInterface
    interface EnvelopeClient {
        EnvelopeResponse generate(String modelId, Content content, GenerateContentConfig config);
    }

    record EnvelopeResponse(String text, boolean truncated) {}
}
