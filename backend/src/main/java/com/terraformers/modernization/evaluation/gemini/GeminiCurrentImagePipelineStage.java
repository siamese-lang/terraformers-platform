package com.terraformers.modernization.evaluation.gemini;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.vertex.VertexGenerationStage;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;

/** Evaluation wrapper that reproduces the current two-image-inference shape with fixed references. */
final class GeminiCurrentImagePipelineStage implements AnalysisGenerationStage {

    private final VertexArchitectureFactsExtractor factsExtractor;
    private final VertexGenerationStage generationStage;

    GeminiCurrentImagePipelineStage(
            VertexArchitectureFactsExtractor factsExtractor,
            VertexGenerationStage generationStage
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
