package com.terraformers.modernization.evaluation.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.vertex.VertexOutputTruncatedException;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.analysis.vertex.VertexResponseFormatException;
import com.terraformers.modernization.analysis.vertex.VertexResponseParser;
import com.terraformers.modernization.reference.ReferenceDocument;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class GeminiFactReuseGenerationStage {

    static final ThinkingLevel.Known THINKING_LEVEL = ThinkingLevel.Known.MEDIUM;

    private final GenerationClient client;
    private final String modelId;
    private final int maxOutputTokens;
    private final ObjectMapper objectMapper;
    private final VertexPromptBuilder schemaSource;
    private final VertexResponseParser responseParser;

    GeminiFactReuseGenerationStage(
            Client client,
            String modelId,
            int maxOutputTokens,
            ObjectMapper objectMapper,
            VertexPromptBuilder schemaSource,
            VertexResponseParser responseParser
    ) {
        this(client, modelId, maxOutputTokens, objectMapper, schemaSource, responseParser, null);
    }

    GeminiFactReuseGenerationStage(
            Client client,
            String modelId,
            int maxOutputTokens,
            ObjectMapper objectMapper,
            VertexPromptBuilder schemaSource,
            VertexResponseParser responseParser,
            GeminiLatencyTelemetry telemetry
    ) {
        this((id, content, config) -> {
            GenerateContentResponse response = client.models.generateContent(id, content, config);
            if (telemetry != null) telemetry.captureSdkResponse(response);
            return new GenerationResponse(response.text(), response.finishReason(),
                    response.usageMetadata().flatMap(metadata -> metadata.candidatesTokenCount()).orElse(null));
        }, modelId, maxOutputTokens, objectMapper, schemaSource, responseParser);
    }

    GeminiFactReuseGenerationStage(
            GenerationClient client,
            String modelId,
            int maxOutputTokens,
            ObjectMapper objectMapper,
            VertexPromptBuilder schemaSource,
            VertexResponseParser responseParser
    ) {
        this.client = client;
        this.modelId = modelId;
        this.maxOutputTokens = maxOutputTokens;
        this.objectMapper = objectMapper;
        this.schemaSource = schemaSource;
        this.responseParser = responseParser;
    }

    AnalysisGenerationResult generate(
            AnalysisRequestContext context,
            GeminiCanonicalEnvelope envelope,
            List<ReferenceDocument> references
    ) {
        try {
            return invoke(envelope, references, false);
        } catch (VertexOutputTruncatedException exception) {
            return invoke(envelope, references, true);
        }
    }

    private AnalysisGenerationResult invoke(
            GeminiCanonicalEnvelope envelope,
            List<ReferenceDocument> references,
            boolean compact
    ) {
        GenerationResponse response = client.generate(
                modelId,
                buildContent(envelope, references, compact),
                config()
        );
        if (response == null) {
            throw new VertexResponseFormatException("fact reuse generation response is empty");
        }
        FinishReason finishReason = response.finishReason();
        FinishReason.Known known = finishReason == null
                ? FinishReason.Known.FINISH_REASON_UNSPECIFIED
                : finishReason.knownEnum();
        if (known == FinishReason.Known.MAX_TOKENS) {
            throw new VertexOutputTruncatedException(response.outputTokens());
        }
        if (known != FinishReason.Known.STOP
                && known != FinishReason.Known.FINISH_REASON_UNSPECIFIED) {
            throw new VertexResponseFormatException(
                    "fact reuse generation stopped before normal completion: " + finishReason);
        }
        if (response.text() == null || response.text().isBlank()) {
            throw new VertexResponseFormatException("fact reuse generation response text is empty");
        }

        try {
            AnalysisGenerationResult parsed = responseParser.parse(
                    "vertex:" + modelId + ":fact-reuse",
                    response.text(),
                    finishReason == null ? "" : finishReason.toString(),
                    response.outputTokens(),
                    compact
            );
            if (parsed.inputClassification() != envelope.inputType()) {
                throw new VertexResponseFormatException(
                        "fact reuse generation changed canonical input classification");
            }
            return parsed;
        } catch (AnalysisInputRejectedException exception) {
            if (exception.retryOccurred() == compact) throw exception;
            throw new AnalysisInputRejectedException(
                    exception.classification(),
                    exception.classificationConfidence(),
                    compact,
                    exception.getCause()
            );
        }
    }

    GenerateContentConfig config() {
        return GenerateContentConfig.builder()
                .maxOutputTokens(maxOutputTokens)
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel(THINKING_LEVEL))
                .responseMimeType("application/json")
                .responseJsonSchema(schemaSource.responseJsonSchema())
                .build();
    }

    Content buildContent(
            GeminiCanonicalEnvelope envelope,
            List<ReferenceDocument> references,
            boolean compact
    ) {
        return Content.fromParts(Part.fromText(buildPrompt(envelope, references, compact)));
    }

    String buildPrompt(
            GeminiCanonicalEnvelope envelope,
            List<ReferenceDocument> references,
            boolean compact
    ) {
        if (envelope.inputType() != com.terraformers.modernization.analysis.AnalysisInputClassification.ARCHITECTURE_DIAGRAM) {
            throw new IllegalArgumentException("fact reuse generation is only valid for architecture envelopes");
        }
        String canonicalJson;
        try {
            canonicalJson = objectMapper.writeValueAsString(Map.of(
                    "inputType", envelope.inputType().name(),
                    "classificationConfidence", envelope.classificationConfidence(),
                    "classificationReason", envelope.classificationReason(),
                    "summary", envelope.summary(),
                    "components", envelope.components(),
                    "relationships", envelope.relationships(),
                    "resourceTypes", envelope.resourceTypes()
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("failed to serialize canonical envelope", exception);
        }

        String referenceText = (references == null ? List.<ReferenceDocument>of() : references).stream()
                .map(this::formatReference)
                .collect(Collectors.joining("\n"));

        return """
                Generate the final response from the canonical image analysis below. No image is attached to this request.
                Return one JSON object matching the supplied response schema.

                Contract:
                - The canonical image analysis is the authoritative result of the vision pass.
                - inputType must remain ARCHITECTURE_DIAGRAM.
                - classificationConfidence and classificationReason must reflect the canonical analysis.
                - Generate Terraform only for components and relationships supported by the canonical analysis.
                - Do not invent visual facts that are absent from the canonical analysis.
                - Do not include secrets, account IDs, access keys, static credentials, public S3 URLs, or real ARNs.
                - Treat PROJECT_DECISION references as mandatory project constraints when applicable.
                - Use PROVIDER_SCHEMA references for AWS Provider 5.100.0 argument and nested-block compatibility.
                - Provider examples demonstrate syntax only; do not copy settings marked by riskTags without adapting them.
                - %s

                Canonical image analysis:
                %s

                Retrieved reference evidence:
                %s
                """.formatted(
                compact
                        ? "Compact mode: minimize prose and Terraform while preserving core resources and relationships."
                        : "Standard mode: keep analysis and Terraform concise and avoid equivalent repeated detail.",
                canonicalJson,
                referenceText.isBlank() ? "- none" : referenceText
        );
    }

    private String formatReference(ReferenceDocument reference) {
        String authority = blankTo(reference.authority(), "REFERENCE");
        String source = blankTo(reference.sourcePath(), reference.id());
        String risks = reference.riskTags().isEmpty() ? "none" : String.join(",", reference.riskTags());
        return "- id=%s; authority=%s; type=%s; source=%s; riskTags=%s; title=%s:\n%s".formatted(
                reference.id(),
                authority,
                blankTo(reference.documentType(), ""),
                source,
                risks,
                reference.title(),
                reference.content()
        );
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    interface GenerationClient {
        GenerationResponse generate(String modelId, Content content, GenerateContentConfig config);
    }

    record GenerationResponse(String text, FinishReason finishReason, Integer outputTokens) {}
}
