package com.terraformers.modernization.analysis;

import java.util.Locale;

public enum AnalysisProviderType {
    STUB, BEDROCK, VERTEX;

    static AnalysisProviderType from(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unsupported terraformers.analysis.provider: " + value, exception);
        }
    }
}
