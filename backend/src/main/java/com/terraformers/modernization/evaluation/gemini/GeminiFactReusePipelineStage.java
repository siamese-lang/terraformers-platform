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
    private Trace lastTrace;

    GeminiFactReusePipelineStage(
            GeminiCanonicalEnvelopeExtractor extractor,
            GeminiFactReuseGenerationStage generationStage
    ) {
        this(extractor::extract, generationStage::generate);
    }

    GeminiFactReusePipelineStage(
            EnvelopeExtractor extractor,
            FactGenerator generationStage
    ) {
        this.extractor = extractor;
        this.generationStage = generationStage;
    }

    @Override
    public AnalysisGenerationResult generate(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references
    ) {
        lastTrace = null;
        long started = System.nanoTime();
        GeminiCanonicalEnvelope envelope = extractor.extract(source);
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
        return generationStage.generate(context, envelope, references);
    }

    Trace lastTraceOrNull() {
        return lastTrace;
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
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
