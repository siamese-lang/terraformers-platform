package com.terraformers.modernization.analysis.bedrock;

import com.terraformers.modernization.analysis.AnalysisGenerationResponseFormatException;

public class BedrockResponseFormatException extends AnalysisGenerationResponseFormatException {

    public BedrockResponseFormatException(String message) {
        super(message);
    }

    public BedrockResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
