package com.terraformers.modernization.analysis.vertex;

public class VertexResponseFormatException extends RuntimeException {

    public VertexResponseFormatException(String message) {
        super(message);
    }

    public VertexResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
