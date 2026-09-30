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

    GeminiCurrentImagePipelineStage(
            ArchitectureFactsExtractor factsExtractor,
            AnalysisGenerationStage generationStage
    ) {
        this.factsExtractor = factsExtractor;
        this.generationStage = generationStage;
    }

    @Override
    public AnalysisGenerationResult generate(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references
    ) {
        factsExtractor.extract(source);
        return generationStage.generate(context, source, references);
    }
}
