package com.terraformers.modernization.analysis.vertex;

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
import com.terraformers.modernization.reference.ReferenceDocument;
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
            @Lazy Client client,
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
        try {
            return invoke(source, safeReferences, false);
        } catch (VertexOutputTruncatedException exception) {
            return invoke(source, safeReferences, true);
        }
    }

    private AnalysisGenerationResult invoke(
            ObjectContent source,
            List<ReferenceDocument> references,
            boolean compact
    ) {
        String modelId = properties.requireGenerationModelId();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(compact ? 0.1f : 0.2f)
                .maxOutputTokens(properties.requireMaxOutputTokens())
                .responseMimeType("application/json")
                .responseJsonSchema(promptBuilder.responseJsonSchema())
                .build();

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
