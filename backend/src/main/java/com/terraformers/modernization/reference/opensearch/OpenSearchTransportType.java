package com.terraformers.modernization.reference.opensearch;

import java.util.Locale;

public enum OpenSearchTransportType {
    HTTP,
    AWS_SIGV4;

    public static OpenSearchTransportType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("terraformers.analysis.opensearch-transport must be set");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Unsupported terraformers.analysis.opensearch-transport: " + value,
                    exception
            );
        }
    }
}
