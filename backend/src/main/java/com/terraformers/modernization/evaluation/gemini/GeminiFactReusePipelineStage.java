package com.terraformers.modernization.evaluation.gemini;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;

final class GeminiFactReusePipelineStage implements AnalysisGenerationStage {

    private final EnvelopeExtractor extractor;
    private final FactGenerator generationStage;
    private final GeminiLatencyTelemetry telemetry;
    private final GeminiLatencyTelemetry.RequestIdentity extractionIdentity;
    private final GeminiLatencyTelemetry.RequestIdentity generationIdentity;
    private Trace lastTrace;

    GeminiFactReusePipelineStage(
            GeminiCanonicalEnvelopeExtractor extractor,
            GeminiFactReuseGenerationStage generationStage
    ) {
        this(extractor::extract, generationStage::generate, null, null, null);
    }

    GeminiFactReusePipelineStage(
            GeminiCanonicalEnvelopeExtractor extractor,
            GeminiFactReuseGenerationStage generationStage,
            GeminiLatencyTelemetry telemetry,
            GeminiLatencyTelemetry.RequestIdentity extractionIdentity,
            GeminiLatencyTelemetry.RequestIdentity generationIdentity
    ) {
        this(extractor::extract, generationStage::generate, telemetry, extractionIdentity, generationIdentity);
    }

    GeminiFactReusePipelineStage(
            EnvelopeExtractor extractor,
            FactGenerator generationStage
    ) {
        this(extractor, generationStage, null, null, null);
    }

    GeminiFactReusePipelineStage(
            EnvelopeExtractor extractor,
            FactGenerator generationStage,
            GeminiLatencyTelemetry telemetry,
            GeminiLatencyTelemetry.RequestIdentity extractionIdentity,
            GeminiLatencyTelemetry.RequestIdentity generationIdentity
    ) {
        this.extractor = extractor;
        this.generationStage = generationStage;
        this.telemetry = telemetry;
        this.extractionIdentity = extractionIdentity;
        this.generationIdentity = generationIdentity;
    }

    @Override
    public AnalysisGenerationResult generate(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references
    ) {
        lastTrace = null;
        long started = System.nanoTime();
        GeminiCanonicalEnvelope envelope = runPhase(
                context.correlationId(), GeminiLatencyTelemetry.Phase.CANONICAL_EXTRACTION,
                extractionIdentity, source, List.of(), () -> extractor.extract(source));
        long extractionLatencyMs = elapsed(started);
        boolean invokeSecondGeneration = envelope.inputType() == AnalysisInputClassification.ARCHITECTURE_DIAGRAM;
        lastTrace = new Trace(
                envelope.inputType().name(),
                envelope.classificationConfidence(),
                envelope.classificationReason(),
                extractionLatencyMs,
                invokeSecondGeneration
        );

        if (!invokeSecondGeneration) {
            throw new AnalysisInputRejectedException(
                    envelope.inputType(),
                    envelope.classificationConfidence(),
                    false,
                    null
            );
        }
        return runPhase(context.correlationId(), GeminiLatencyTelemetry.Phase.GENERATION,
                generationIdentity, null, references,
                () -> generationStage.generate(context, envelope, references));
    }

    Trace lastTraceOrNull() {
        return lastTrace;
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
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
        int imageBytes = source == null ? 0 : source.bytes().length;
        String modality = imageBytes == 0 ? "TEXT" : "IMAGE_AND_TEXT";
        var scope = telemetry.start(caseId, GeminiLatencyTelemetry.Arm.CANDIDATE, phase, identity,
                GeminiLatencyTelemetry.PayloadShape.of(modality, imageBytes, "", referenceTexts));
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

    @FunctionalInterface
    interface EnvelopeExtractor {
        GeminiCanonicalEnvelope extract(ObjectContent source);
    }

    @FunctionalInterface
    interface FactGenerator {
        AnalysisGenerationResult generate(
                AnalysisRequestContext context,
                GeminiCanonicalEnvelope envelope,
                List<ReferenceDocument> references
        );
    }

    record Trace(
            String canonicalInputType,
            double canonicalClassificationConfidence,
            String canonicalClassificationReason,
            long canonicalExtractionLatencyMs,
            boolean secondGenerationInvoked
    ) {}
}
