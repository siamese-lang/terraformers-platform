package com.terraformers.modernization.analysis;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import com.terraformers.modernization.security.JwtRuntimeProperties;
import com.terraformers.modernization.security.FirebaseJwtRuntimeProperties;
import com.terraformers.modernization.storage.StorageRuntimeProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Fails startup for unsupported selectors without initializing a provider adapter. */
@Component
public class ProviderSelectionValidator {
    private final AnalysisRuntimeProperties properties;
    private final JwtRuntimeProperties jwtProperties;
    private final StorageRuntimeProperties storageProperties;
    private final FirebaseJwtRuntimeProperties firebaseProperties;
    private final Environment environment;

    public ProviderSelectionValidator(AnalysisRuntimeProperties properties, JwtRuntimeProperties jwtProperties,
                                      StorageRuntimeProperties storageProperties,
                                      FirebaseJwtRuntimeProperties firebaseProperties, Environment environment) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
        this.storageProperties = storageProperties;
        this.firebaseProperties = firebaseProperties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        properties.resolvedProvider();
        properties.resolvedEmbeddingProvider();
        properties.resolvedProgressPublisher();
        if (!jwtProperties.isCognito() && !jwtProperties.isFirebase()) {
            throw new IllegalStateException("Unsupported terraformers.security.jwt.provider: " + jwtProperties.getProvider());
        }
        if (environment.acceptsProfiles(Profiles.of("prod")) && !jwtProperties.isEnabled()) {
            throw new IllegalStateException("production JWT authentication cannot be disabled");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && environment.acceptsProfiles(Profiles.of("test"))) {
            throw new IllegalStateException("test profile cannot be combined with production JWT authentication");
        }
        if (jwtProperties.isFirebase()) {
            String projectId = firebaseProperties.getProjectId();
            if (!jwtProperties.isEnabled() || projectId == null
                    || !projectId.matches("[a-z][a-z0-9-]{4,29}")
                    || !("https://securetoken.google.com/" + projectId).equals(jwtProperties.getIssuerUri())
                    || (!environment.acceptsProfiles(Profiles.of("test"))
                        && !FirebaseJwtRuntimeProperties.JWK_SET_URI.equals(jwtProperties.getJwkSetUri()))) {
                throw new IllegalStateException("Firebase JWT release configuration is incomplete or unsafe");
            }
        }
        storageProperties.resolvedReaderProvider();
        storageProperties.resolvedWriterProvider();
    }
}
