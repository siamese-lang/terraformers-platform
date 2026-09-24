package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.analysis.AnalysisGenerationOutputTruncatedException;

public class VertexOutputTruncatedException extends AnalysisGenerationOutputTruncatedException {

    private final Integer outputTokens;

    public VertexOutputTruncatedException(Integer outputTokens) {
        super("Vertex AI response reached the configured maximum output tokens");
        this.outputTokens = outputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }
}
