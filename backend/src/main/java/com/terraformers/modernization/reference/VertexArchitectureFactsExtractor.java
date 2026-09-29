package com.terraformers.modernization.reference;

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
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import com.terraformers.modernization.storage.ObjectContent;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class VertexArchitectureFactsExtractor implements ArchitectureFactsExtractor {

    public static final int MAX_FACT_TOKENS = 800;
    public static final ThinkingLevel.Known FACT_EXTRACTION_THINKING_LEVEL = ThinkingLevel.Known.LOW;
    private static final String FACTS_PROMPT = """
            Return one compact JSON object only with keys summary, components, relationships, resourceTypes.
            Keep summary under 160 characters. Keep each array to at most 8 strings and each string under 60 characters.
            Describe architecture facts only; never generate Terraform, Markdown, or explanatory prose.
            """;

    private static final Set<Integer> TRANSIENT_HTTP_STATUSES = Set.of(408, 429, 500, 502, 503, 504);

    private final VertexFactsClient factsClient;
    private final ObjectMapper objectMapper;
    private final VertexRuntimeProperties properties;

    @Autowired
    public VertexArchitectureFactsExtractor(
            @Lazy Client client,
            ObjectMapper objectMapper,
            VertexRuntimeProperties properties
    ) {
        this((modelId, content, config) -> {
            GenerateContentResponse response = client.models.generateContent(modelId, content, config);
            boolean truncated = response.finishReason() != null
                    && response.finishReason().knownEnum() == FinishReason.Known.MAX_TOKENS;
            return new VertexFactsResponse(response.text(), truncated);
        }, objectMapper, properties);
    }

    VertexArchitectureFactsExtractor(
            VertexFactsClient factsClient,
            ObjectMapper objectMapper,
            VertexRuntimeProperties properties
    ) {
        this.factsClient = factsClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ArchitectureRetrievalFacts extract(ObjectContent source) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.0f)
                .maxOutputTokens(MAX_FACT_TOKENS)
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel(FACT_EXTRACTION_THINKING_LEVEL))
                .responseMimeType("application/json")
                .responseJsonSchema(responseJsonSchema())
                .build();
        Content content = Content.fromParts(
                Part.fromBytes(source.bytes(), source.metadata().contentType()),
                Part.fromText(FACTS_PROMPT)
        );
        VertexFactsResponse response;
        try {
            response = factsClient.generate(
                    properties.requireGenerationModelId(),
                    content,
                    config
            );
        } catch (RuntimeException exception) {
            throw providerFailure(exception);
        }
        if (response == null) {
            throw ArchitectureFactsExtractionException.response(
                    ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE, null);
        }
        if (response.truncated()) {
            throw ArchitectureFactsExtractionException.response(
                    ArchitectureFactsExtractionException.Reason.RESPONSE_TRUNCATED, null);
        }
        String responseText = response.text();
        if (responseText == null || responseText.isBlank()) {
            throw ArchitectureFactsExtractionException.response(
                    ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE, null);
        }
        try {
            JsonNode facts = objectMapper.readTree(responseText);
            if (!facts.isObject()) {
                throw invalidResponse(null);
            }
            ArchitectureRetrievalFacts result = new ArchitectureRetrievalFacts(
                    text(facts, "summary"),
                    strings(facts, "components"),
                    strings(facts, "relationships"),
                    strings(facts, "resourceTypes")
            );
            if (result.isEmpty()) {
                throw ArchitectureFactsExtractionException.response(
                        ArchitectureFactsExtractionException.Reason.EMPTY_FACTS, null);
            }
            return result;
        } catch (ArchitectureFactsExtractionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidResponse(exception);
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
            throw invalidResponse(null);
        }
        return value.asText().strip();
    }

    private List<String> strings(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            throw invalidResponse(null);
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw invalidResponse(null);
            }
            String text = value.asText().strip();
            if (!text.isBlank()) values.add(text);
        }
        return List.copyOf(values);
    }

    private ArchitectureFactsExtractionException invalidResponse(Throwable cause) {
        return ArchitectureFactsExtractionException.response(
                ArchitectureFactsExtractionException.Reason.INVALID_RESPONSE, cause);
    }

    private ArchitectureFactsExtractionException providerFailure(RuntimeException exception) {
        Integer status = googleStatusCode(exception);
        return ArchitectureFactsExtractionException.providerRuntime(
                status == null ? "" : Integer.toString(status),
                safeErrorType(exception),
                status == null ? null : TRANSIENT_HTTP_STATUSES.contains(status),
                exception
        );
    }

    private Integer googleStatusCode(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            Package typePackage = current.getClass().getPackage();
            if (typePackage == null || !typePackage.getName().startsWith("com.google")) {
                continue;
            }
            for (String methodName : List.of("code", "statusCode", "getStatusCode")) {
                try {
                    Method method = current.getClass().getMethod(methodName);
                    Object value = method.invoke(current);
                    if (value instanceof Number number && number.intValue() >= 100 && number.intValue() <= 599) {
                        return number.intValue();
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) {
                    // The SDK exception does not expose this status accessor.
                }
            }
        }
        return null;
    }

    private String safeErrorType(RuntimeException exception) {
        String name = exception.getClass().getSimpleName();
        return name.matches("[A-Za-z0-9_.-]{1,80}") ? name : "RuntimeException";
    }

    @FunctionalInterface
    interface VertexFactsClient {
        VertexFactsResponse generate(String modelId, Content content, GenerateContentConfig config);
    }

    record VertexFactsResponse(String text, boolean truncated) {
    }
}
