package com.terraformers.modernization.analysis;

import java.util.Locale;

public enum ProgressPublisherType {
    LOGGING, SQS;

    static ProgressPublisherType from(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unsupported terraformers.analysis.progress-publisher: " + value, exception);
        }
    }
}
