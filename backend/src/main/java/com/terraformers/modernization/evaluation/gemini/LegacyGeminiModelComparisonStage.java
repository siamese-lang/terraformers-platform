package com.terraformers.modernization.evaluation.gemini;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.vertex.VertexOutputTruncatedException;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.analysis.vertex.VertexResponseFormatException;
import com.terraformers.modernization.analysis.vertex.VertexResponseParser;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;

/**
 * Evaluation-only reproduction of the original frozen MODEL comparison generation settings.
 *
 * <p>Production Gemini 3.8 generation intentionally no longer sends temperature. This class exists
 * only so the historical Flash-vs-Pro comparison can still be rerun with its original standard
 * temperature 0.2 and compact-retry temperature 0.1 contract.
 */
final class LegacyGeminiModelComparisonStage implements AnalysisGenerationStage {

    static final float STANDARD_TEMPERATURE = 0.2f;
    static final float COMPACT_TEMPERATURE = 0.1f;

    private final Client client;
    private final String modelId;
    private final int maxOutputTokens;
    private final VertexPromptBuilder promptBuilder;
    private final VertexResponseParser responseParser;

    LegacyGeminiModelComparisonStage(
            Client client,
            String modelId,
            int maxOutputTokens,
            VertexPromptBuilder promptBuilder,
            VertexResponseParser responseParser
    ) {
        this.client = client;
        this.modelId = modelId;
        this.maxOutputTokens = maxOutputTokens;
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
        try {
            return invoke(source, safeReferences, false);
        } catch (VertexOutputTruncatedException exception) {
            return invoke(source, safeReferences, true);
        }
    }

    GenerateContentConfig generationConfig(boolean compact) {
        return GenerateContentConfig.builder()
                .temperature(compact ? COMPACT_TEMPERATURE : STANDARD_TEMPERATURE)
                .maxOutputTokens(maxOutputTokens)
                .responseMimeType("application/json")
                .responseJsonSchema(promptBuilder.responseJsonSchema())
                .build();
    }

    private AnalysisGenerationResult invoke(
            ObjectContent source,
            List<ReferenceDocument> references,
            boolean compact
    ) {
        GenerateContentConfig config = generationConfig(compact);
        Content content = Content.fromParts(
                Part.fromBytes(source.bytes(), source.metadata().contentType()),
                Part.fromText(promptBuilder.build(source, references, compact))
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
                    compact
            );
        } catch (AnalysisInputRejectedException exception) {
            if (exception.retryOccurred() == compact) {
                throw exception;
            }
            throw new AnalysisInputRejectedException(
                    exception.classification(),
                    exception.classificationConfidence(),
                    compact,
                    exception.getCause()
            );
        }
    }
}
