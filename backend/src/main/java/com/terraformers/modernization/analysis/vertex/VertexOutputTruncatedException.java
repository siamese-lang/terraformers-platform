package com.terraformers.modernization.analysis.vertex;

public class VertexOutputTruncatedException extends RuntimeException {

    private final Integer outputTokens;

    public VertexOutputTruncatedException(Integer outputTokens) {
        super("Vertex AI response reached the configured maximum output tokens");
        this.outputTokens = outputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }
}
