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
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import java.util.Map;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;
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
        return generate(context, source, references, new AwsProviderSchemaEvidence(Map.of()));
    }

    public AnalysisGenerationResult generate(AnalysisRequestContext context, ObjectContent source,
            List<ReferenceDocument> references, AwsProviderSchemaEvidence schemaEvidence) {
        List<ReferenceDocument> safeReferences = references == null ? List.of() : List.copyOf(references);
        try {
            return invoke(source, safeReferences, schemaEvidence, false, false, false);
        } catch (VertexOutputTruncatedException exception) {
            return invoke(source, safeReferences, schemaEvidence, true, true, false);
        }
    }

    public AnalysisGenerationResult regenerateAfterSensitiveCredential(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references
    ) {
        return regenerateAfterSensitiveCredential(context, source, references,
                new AwsProviderSchemaEvidence(Map.of()));
    }

    public AnalysisGenerationResult regenerateAfterSensitiveCredential(AnalysisRequestContext context,
            ObjectContent source, List<ReferenceDocument> references, AwsProviderSchemaEvidence schemaEvidence) {
        List<ReferenceDocument> safeReferences = references == null ? List.of() : List.copyOf(references);
        return invoke(source, safeReferences, schemaEvidence, false, true, true);
    }

    GenerateContentConfig generationConfig() {
        var builder = GenerateContentConfig.builder()
                .maxOutputTokens(properties.requireMaxOutputTokens())
                .responseMimeType("application/json")
                .responseJsonSchema(promptBuilder.responseJsonSchema());
        properties.resolvedGenerationThinkingLevel().ifPresent(level ->
                builder.thinkingConfig(ThinkingConfig.builder().thinkingLevel(level)));
        return builder.build();
    }

    AnalysisGenerationResult invoke(
            ObjectContent source,
            List<ReferenceDocument> references,
            AwsProviderSchemaEvidence schemaEvidence,
            boolean compact,
            boolean retryOccurred,
            boolean sensitiveCredentialRecovery
    ) {
        String modelId = properties.requireGenerationModelId();
        GenerateContentConfig config = generationConfig();

        Content content = Content.fromParts(
                Part.fromBytes(source.bytes(), source.metadata().contentType()),
                Part.fromText(sensitiveCredentialRecovery
                        ? promptBuilder.buildSensitiveCredentialRecovery(source, references, schemaEvidence)
                        : promptBuilder.build(source, references, schemaEvidence, compact))
        );

        GenerateContentResponse response = client.models.generateContent(modelId, content, config);
        Integer outputTokens = response.usageMetadata()
                .flatMap(metadata -> metadata.candidatesTokenCount())
                .orElse(null);
        FinishReason finishReason = response.finishReason();
        FinishReason.Known known = finishReason == null
                ? FinishReason.Known.FINISH_REASON_UNSPECIFIED
                : finishReason.knownEnum();

        if (known == FinishReason.Known.MAX_TOKENS) {
            throw new VertexOutputTruncatedException(outputTokens);
        }
        if (known != FinishReason.Known.STOP
                && known != FinishReason.Known.FINISH_REASON_UNSPECIFIED) {
            throw new VertexResponseFormatException(
                    "Vertex generation stopped before a normal completion: " + finishReason);
        }

        String text = response.text();
        if (text == null || text.isBlank()) {
            throw new VertexResponseFormatException("Vertex response text is empty");
        }

        try {
            return responseParser.parse(
                    "vertex:" + modelId,
                    text,
                    finishReason == null ? "" : finishReason.toString(),
                    outputTokens,
                    retryOccurred
            );
        } catch (AnalysisInputRejectedException exception) {
            if (exception.retryOccurred() == retryOccurred) {
                throw exception;
            }
            throw new AnalysisInputRejectedException(
                    exception.classification(),
                    exception.classificationConfidence(),
                    retryOccurred,
                    exception.getCause()
            );
        }
    }
}
