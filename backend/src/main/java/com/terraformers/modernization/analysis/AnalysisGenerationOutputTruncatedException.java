package com.terraformers.modernization.analysis;

public class AnalysisGenerationOutputTruncatedException extends RuntimeException {

    public AnalysisGenerationOutputTruncatedException(String message) {
        super(message);
    }

    public AnalysisGenerationOutputTruncatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
