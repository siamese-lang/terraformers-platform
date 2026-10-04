package com.terraformers.modernization.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.bedrock.BedrockRuntimeProperties;
import com.terraformers.modernization.analysis.ProviderFailureClassifier;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;

/** Bounded vision stage used only to derive retrieval facts, never Terraform. */
@Component
public class BedrockArchitectureFactsExtractor implements ArchitectureFactsExtractor {
    private static final int MAX_FACT_TOKENS = 800;
    private static final String FACTS_PROMPT = "Return one compact JSON object only with keys summary,components,relationships,resourceTypes. "
            + "Keep summary under 160 characters. Keep each array to at most 8 strings and each string under 60 characters. "
            + "Describe architecture facts only; never generate Terraform, Markdown, or explanatory prose.";

    private final BedrockRuntimeClient client;
    private final ObjectMapper objectMapper;
    private final BedrockRuntimeProperties properties;

    public BedrockArchitectureFactsExtractor(@Lazy BedrockRuntimeClient client, ObjectMapper objectMapper,
                                             BedrockRuntimeProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public ArchitectureRetrievalFacts extract(ObjectContent source) {
        try {
            String mediaType = source.metadata().contentType();
            String request = objectMapper.writeValueAsString(Map.of(
                    "anthropic_version", "bedrock-2023-05-31", "max_tokens", MAX_FACT_TOKENS, "temperature", 0,
                    "messages", List.of(Map.of("role", "user", "content", List.of(
                            Map.of("type", "image", "source", Map.of("type", "base64", "media_type", mediaType,
                                    "data", Base64.getEncoder().encodeToString(source.bytes()))),
                            Map.of("type", "text", "text", FACTS_PROMPT))))));
            String response = client.invokeModel(InvokeModelRequest.builder().modelId(requireModelId())
                    .contentType("application/json").accept("application/json").body(SdkBytes.fromUtf8String(request)).build())
                    .body().asUtf8String();
            if (response == null || response.isBlank()) {
                throw ArchitectureFactsExtractionException.response(
                        ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE, null);
            }
            JsonNode root = objectMapper.readTree(response);
            if ("max_tokens".equals(root.path("stop_reason").asText())) {
                throw ArchitectureFactsExtractionException.response(
                        ArchitectureFactsExtractionException.Reason.RESPONSE_TRUNCATED, null);
            }
            JsonNode content = root.path("content");
            if (!content.isArray()) throw ArchitectureFactsExtractionException.response(
                    ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE, null);
            JsonNode textBlock = null;
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText()) && block.path("text").isTextual()) { textBlock = block; break; }
            }
            if (textBlock == null || textBlock.path("text").asText().isBlank()) {
                throw ArchitectureFactsExtractionException.response(
                        ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE, null);
            }
            String text = extractFactsJsonObject(textBlock.path("text").asText());
            JsonNode facts = objectMapper.readTree(text);
            if (!facts.isObject()) throw invalid(null);
            if (!facts.path("summary").isMissingNode() && !facts.path("summary").isTextual()) throw invalid(null);
            ArchitectureRetrievalFacts result = new ArchitectureRetrievalFacts(facts.path("summary").asText(""), strings(facts.path("components")),
                    strings(facts.path("relationships")), strings(facts.path("resourceTypes")));
            if (result.isEmpty()) throw ArchitectureFactsExtractionException.response(
                    ArchitectureFactsExtractionException.Reason.EMPTY_FACTS, null);
            return result;
        } catch (ArchitectureFactsExtractionException exception) {
            throw exception;
        } catch (ApiCallAttemptTimeoutException | ApiCallTimeoutException exception) {
            throw provider(ArchitectureFactsExtractionException.Reason.PROVIDER_TIMEOUT, exception);
        } catch (RuntimeException exception) {
            throw provider(ProviderFailureClassifier.isTimeout(exception)
                    ? ArchitectureFactsExtractionException.Reason.PROVIDER_TIMEOUT
                    : ProviderFailureClassifier.isRateLimited(exception)
                    ? ArchitectureFactsExtractionException.Reason.PROVIDER_RATE_LIMITED
                    : ArchitectureFactsExtractionException.Reason.PROVIDER_ERROR, exception);
        } catch (Exception exception) {
            throw invalid(exception);
        }
    }

    private ArchitectureFactsExtractionException invalid(Throwable cause) {
        return ArchitectureFactsExtractionException.response(
                ArchitectureFactsExtractionException.Reason.INVALID_RESPONSE, cause);
    }

    private ArchitectureFactsExtractionException provider(
            ArchitectureFactsExtractionException.Reason reason, RuntimeException cause) {
        Integer status = ProviderFailureClassifier.statusCode(cause);
        return ArchitectureFactsExtractionException.provider(reason,
                status == null ? "" : status.toString(), cause.getClass().getSimpleName(), cause);
    }

    private String extractFactsJsonObject(String responseText) {
        String normalized = responseText.strip();
        int objectStart = normalized.indexOf('{');
        if (objectStart < 0) throw invalid(null);

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = objectStart; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) return normalized.substring(objectStart, index + 1);
            }
        }
        throw invalid(null);
    }

    private String requireModelId() {
        String id = properties.getModelId();
        if (id == null || id.isBlank()) throw new IllegalStateException("terraformers.analysis.bedrock.model-id must be set for retrieval facts");
        return id.strip();
    }

    private List<String> strings(JsonNode node) {
        if (node.isMissingNode()) return List.of();
        if (!node.isArray()) throw invalid(null);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        node.forEach(value -> {
            if (!value.isTextual()) throw invalid(null);
            values.add(value.asText());
        });
        return values;
    }
}
