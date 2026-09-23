package com.terraformers.modernization.analysis;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Fails startup for unsupported selectors without initializing a provider adapter. */
@Component
public class ProviderSelectionValidator {
    private final AnalysisRuntimeProperties properties;

    public ProviderSelectionValidator(AnalysisRuntimeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        properties.resolvedProvider();
        properties.resolvedEmbeddingProvider();
    }
}
