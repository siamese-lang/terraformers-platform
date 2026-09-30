package com.terraformers.modernization.evaluation.gemini;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.reference.ArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;

/** Evaluation wrapper that reproduces the current two-image-inference shape with fixed references. */
final class GeminiCurrentImagePipelineStage implements AnalysisGenerationStage {

    private final ArchitectureFactsExtractor factsExtractor;
    private final AnalysisGenerationStage generationStage;
    private final GeminiLatencyTelemetry telemetry;
    private final GeminiLatencyTelemetry.RequestIdentity factsIdentity;
    private final GeminiLatencyTelemetry.RequestIdentity generationIdentity;

    GeminiCurrentImagePipelineStage(
            ArchitectureFactsExtractor factsExtractor,
            AnalysisGenerationStage generationStage
    ) {
        this(factsExtractor, generationStage, null, null, null);
    }

    GeminiCurrentImagePipelineStage(
            ArchitectureFactsExtractor factsExtractor,
            AnalysisGenerationStage generationStage,
            GeminiLatencyTelemetry telemetry,
            GeminiLatencyTelemetry.RequestIdentity factsIdentity,
            GeminiLatencyTelemetry.RequestIdentity generationIdentity
    ) {
        this.factsExtractor = factsExtractor;
        this.generationStage = generationStage;
        this.telemetry = telemetry;
        this.factsIdentity = factsIdentity;
        this.generationIdentity = generationIdentity;
    }

    @Override
    public AnalysisGenerationResult generate(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references
    ) {
        runPhase(context.correlationId(), GeminiLatencyTelemetry.Phase.FACT_EXTRACTION,
                factsIdentity, source, List.of(), () -> factsExtractor.extract(source));
        return runPhase(context.correlationId(), GeminiLatencyTelemetry.Phase.GENERATION,
                generationIdentity, source, references,
                () -> generationStage.generate(context, source, references));
    }

    private <T> T runPhase(
            String caseId,
            GeminiLatencyTelemetry.Phase phase,
            GeminiLatencyTelemetry.RequestIdentity identity,
            ObjectContent source,
            List<ReferenceDocument> references,
            java.util.function.Supplier<T> operation
    ) {
        if (telemetry == null) return operation.get();
        List<String> referenceTexts = references == null ? List.of() : references.stream()
                .map(reference -> reference.content() == null ? "" : reference.content()).toList();
        var scope = telemetry.start(caseId, GeminiLatencyTelemetry.Arm.CONTROL, phase, identity,
                GeminiLatencyTelemetry.PayloadShape.of("IMAGE_AND_TEXT", source.bytes().length, "", referenceTexts));
        Throwable failure = null;
        try {
            return operation.get();
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            scope.close(failure);
        }
    }
}
