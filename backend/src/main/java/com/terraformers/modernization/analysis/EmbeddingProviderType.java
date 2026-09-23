package com.terraformers.modernization.analysis;

import java.util.Locale;

public enum EmbeddingProviderType {
    BEDROCK, DISABLED;

    static EmbeddingProviderType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("terraformers.analysis.embedding-provider must be set");
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unsupported terraformers.analysis.embedding-provider: " + value, exception);
        }
    }
}
