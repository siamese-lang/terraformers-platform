package com.terraformers.modernization.analysis;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import com.terraformers.modernization.security.JwtRuntimeProperties;
import com.terraformers.modernization.storage.StorageRuntimeProperties;

/** Fails startup for unsupported selectors without initializing a provider adapter. */
@Component
public class ProviderSelectionValidator {
    private final AnalysisRuntimeProperties properties;
    private final JwtRuntimeProperties jwtProperties;
    private final StorageRuntimeProperties storageProperties;

    public ProviderSelectionValidator(AnalysisRuntimeProperties properties, JwtRuntimeProperties jwtProperties,
                                      StorageRuntimeProperties storageProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    void validate() {
        properties.resolvedProvider();
        properties.resolvedEmbeddingProvider();
        properties.resolvedProgressPublisher();
        if (!jwtProperties.isCognito()) {
            throw new IllegalStateException("Unsupported terraformers.security.jwt.provider: " + jwtProperties.getProvider());
        }
        storageProperties.resolvedReaderProvider();
        storageProperties.resolvedWriterProvider();
    }
}
