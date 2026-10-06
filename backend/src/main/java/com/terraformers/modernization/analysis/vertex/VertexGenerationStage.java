package com.terraformers.modernization.analysis.vertex;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisProviderTimeoutException;
import com.terraformers.modernization.analysis.ProviderFailureClassifier;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class VertexGenerationStage implements AnalysisGenerationStage {

    private final Client client;
    private final VertexRuntimeProperties properties;
    private final VertexPromptBuilder promptBuilder;
    private final VertexResponseParser responseParser;

    public VertexGenerationStage(
            Client client,
            VertexRuntimeProperties properties,
            VertexPromptBuilder promptBuilder,
            VertexResponseParser responseParser
    ) {
        this.client = client;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    @Override
    public AnalysisGenerationResult generate(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references
    ) {
        List<ReferenceDocument> safeReferences = references == null ? List.of() : List.copyOf(references);
        AwsProviderSchemaEvidence schemaEvidence = new AwsProviderSchemaEvidence(Map.of());
        try {
            return invoke(source, safeReferences, schemaEvidence, false, false);
        } catch (VertexOutputTruncatedException exception) {
            return invoke(source, safeReferences, schemaEvidence, true, true);
        }
    }

    public AnalysisGenerationResult generate(AnalysisRequestContext context, ObjectContent source,
            List<ReferenceDocument> references, AwsProviderSchemaEvidence schemaEvidence) {
        List<ReferenceDocument> safeReferences = references == null ? List.of() : List.copyOf(references);
        // This production path reserves the second call for one grounding repair.
        return invoke(source, safeReferences, schemaEvidence, false, false);
    }

    GenerateContentConfig generationConfig() {
        return generationConfig(promptBuilder.responseJsonSchema());
    }

    private GenerateContentConfig generationConfig(Map<String, Object> responseSchema) {
        var builder = GenerateContentConfig.builder()
                .maxOutputTokens(properties.requireMaxOutputTokens())
                .responseMimeType("application/json")
                .responseJsonSchema(responseSchema);
        properties.resolvedGenerationThinkingLevel().ifPresent(level ->
                builder.thinkingConfig(ThinkingConfig.builder().thinkingLevel(level)));
        return builder.build();
    }

    public String repair(ArchitectureRetrievalFacts facts, AnalysisGenerationResult original,
            List<ReferenceDocument> references, AwsProviderSchemaEvidence schemaEvidence) {
        Content content = Content.fromParts(Part.fromText(
                promptBuilder.buildRepair(facts, original, references, schemaEvidence)));
        GenerateContentResponse response = completedResponse(content,
                generationConfig(promptBuilder.repairResponseJsonSchema()));
        return responseParser.parseTerraformRepair(requireResponseText(response.text()));
    }

    AnalysisGenerationResult invoke(
            ObjectContent source,
            List<ReferenceDocument> references,
            AwsProviderSchemaEvidence schemaEvidence,
            boolean compact,
            boolean retryOccurred
    ) {
        String modelId = properties.requireGenerationModelId();
        GenerateContentConfig config = generationConfig();

        Content content = Content.fromParts(
                Part.fromBytes(source.bytes(), source.metadata().contentType()),
                Part.fromText(promptBuilder.build(source, references, schemaEvidence, compact))
        );

        GenerateContentResponse response = completedResponse(content, config);
        Integer outputTokens = response.usageMetadata()
                .flatMap(metadata -> metadata.candidatesTokenCount()).orElse(null);
        FinishReason finishReason = response.finishReason();
        String text = requireResponseText(response.text());

        try {
            return responseParser.parse(
                    "vertex:" + modelId, text,
                    finishReason == null ? "" : finishReason.toString(), outputTokens, retryOccurred);
        } catch (AnalysisInputRejectedException exception) {
            if (exception.retryOccurred() == retryOccurred) throw exception;
            throw new AnalysisInputRejectedException(exception.classification(),
                    exception.classificationConfidence(), retryOccurred, exception.getCause());
        }
    }

    private GenerateContentResponse completedResponse(Content content, GenerateContentConfig config) {
        GenerateContentResponse response;
        try {
            response = request(content, config);
        } catch (RuntimeException exception) {
            throw providerCallFailure(exception);
        }
        Integer outputTokens = response.usageMetadata()
                .flatMap(metadata -> metadata.candidatesTokenCount())
                .orElse(null);
        FinishReason finishReason = response.finishReason();
        FinishReason.Known known = finishReason == null
                ? FinishReason.Known.FINISH_REASON_UNSPECIFIED
                : finishReason.knownEnum();

        requireNormalCompletion(known, finishReason, outputTokens);
        return response;
    }

    GenerateContentResponse request(Content content, GenerateContentConfig config) {
        return client.models.generateContent(properties.requireGenerationModelId(), content, config);
    }

    RuntimeException providerCallFailure(RuntimeException exception) {
        if (ProviderFailureClassifier.isTimeout(exception)) {
            return new AnalysisProviderTimeoutException(exception);
        }
        AnalysisProviderFailureReason reason = ProviderFailureClassifier.isRateLimited(exception)
                ? AnalysisProviderFailureReason.RATE_LIMITED
                : AnalysisProviderFailureReason.PROVIDER_ERROR;
        return new AnalysisProviderFailureException(reason, exception);
    }

    void requireNormalCompletion(FinishReason.Known known, FinishReason finishReason, Integer outputTokens) {
        if (known == FinishReason.Known.MAX_TOKENS) {
            throw new VertexOutputTruncatedException(outputTokens);
        }
        if (isContentBlocked(known)) {
            throw new AnalysisProviderFailureException(AnalysisProviderFailureReason.CONTENT_BLOCKED, null);
        }
        if (known != FinishReason.Known.STOP
                && known != FinishReason.Known.FINISH_REASON_UNSPECIFIED) {
            throw new VertexResponseFormatException(
                    "Vertex generation stopped before a normal completion: " + finishReason);
        }
    }

    String requireResponseText(String text) {
        if (text == null || text.isBlank()) {
            throw new AnalysisProviderFailureException(AnalysisProviderFailureReason.EMPTY_RESPONSE, null);
        }
        return text;
    }

    public static boolean isContentBlocked(FinishReason.Known reason) {
        return switch (reason) {
            case SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT, SPII, IMAGE_SAFETY,
                    IMAGE_PROHIBITED_CONTENT, IMAGE_RECITATION -> true;
            default -> false;
        };
    }
}
