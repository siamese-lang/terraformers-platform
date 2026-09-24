package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.analysis.AnalysisGenerationResponseFormatException;

public class VertexResponseFormatException extends AnalysisGenerationResponseFormatException {

    public VertexResponseFormatException(String message) {
        super(message);
    }

    public VertexResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
