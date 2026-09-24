package com.terraformers.modernization.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class VertexArchitectureFactsExtractor implements ArchitectureFactsExtractor {

    private static final int MAX_FACT_TOKENS = 800;
    private static final String FACTS_PROMPT = """
            Return one compact JSON object only with keys summary, components, relationships, resourceTypes.
            Keep summary under 160 characters. Keep each array to at most 8 strings and each string under 60 characters.
            Describe architecture facts only; never generate Terraform, Markdown, or explanatory prose.
            """;

    private final Client client;
    private final ObjectMapper objectMapper;
    private final VertexRuntimeProperties properties;

    public VertexArchitectureFactsExtractor(
            @Lazy Client client,
            ObjectMapper objectMapper,
            VertexRuntimeProperties properties
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ArchitectureRetrievalFacts extract(ObjectContent source) {
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(0.0f)
                    .maxOutputTokens(MAX_FACT_TOKENS)
                    .responseMimeType("application/json")
                    .responseJsonSchema(responseJsonSchema())
                    .build();
            Content content = Content.fromParts(
                    Part.fromBytes(source.bytes(), source.metadata().contentType()),
                    Part.fromText(FACTS_PROMPT)
            );
            GenerateContentResponse response = client.models.generateContent(
                    properties.requireGenerationModelId(),
                    content,
                    config
            );
            if (response.finishReason() != null
                    && response.finishReason().knownEnum() == FinishReason.Known.MAX_TOKENS) {
                throw new IllegalStateException("Vertex facts response reached max output tokens");
            }
            String responseText = response.text();
            if (responseText == null || responseText.isBlank()) {
                throw new IllegalStateException("Vertex facts response text is empty");
            }
            JsonNode facts = objectMapper.readTree(responseText);
            if (!facts.isObject()) {
                throw new IllegalStateException("Vertex facts response is not a JSON object");
            }
            ArchitectureRetrievalFacts result = new ArchitectureRetrievalFacts(
                    text(facts, "summary"),
                    strings(facts, "components"),
                    strings(facts, "relationships"),
                    strings(facts, "resourceTypes")
            );
            if (result.isEmpty()) {
                throw new IllegalStateException("Vertex facts response is empty");
            }
            return result;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("failed to extract Vertex architecture retrieval facts", exception);
        }
    }

    private Map<String, Object> responseJsonSchema() {
        Map<String, Object> array = Map.of("type", "array", "items", Map.of("type", "string"));
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "summary", Map.of("type", "string"),
                        "components", array,
                        "relationships", array,
                        "resourceTypes", array
                ),
                "required", List.of("summary", "components", "relationships", "resourceTypes"),
                "additionalProperties", false
        );
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual()) {
            throw new IllegalStateException("Vertex facts field " + field + " must be text");
        }
        return value.asText().strip();
    }

    private List<String> strings(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            throw new IllegalStateException("Vertex facts field " + field + " must be an array");
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalStateException("Vertex facts field " + field + " must contain only strings");
            }
            String text = value.asText().strip();
            if (!text.isBlank()) values.add(text);
        }
        return List.copyOf(values);
    }
}
