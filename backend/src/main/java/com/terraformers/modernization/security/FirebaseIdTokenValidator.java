package com.terraformers.modernization.security;

import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "terraformers.security.jwt.provider", havingValue = "firebase")
public class FirebaseIdTokenValidator implements JwtProviderTokenValidator {
    private final String projectId;

    public FirebaseIdTokenValidator(FirebaseJwtRuntimeProperties properties) {
        this.projectId = properties.getProjectId();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String subject = jwt.getSubject();
        Object issuedAt = jwt.getClaim("iat");
        Object authTime = jwt.getClaim("auth_time");
        Object firebase = jwt.getClaim("firebase");
        Instant now = Instant.now();
        if (!jwt.getAudience().equals(java.util.List.of(projectId))
                || subject == null || subject.isBlank() || subject.length() > 128
                || !(issuedAt instanceof Instant issued) || issued.isAfter(now)
                || !(authTime instanceof Number authenticated) || authenticated.longValue() <= 0
                || authenticated.longValue() > issued.getEpochSecond()
                || !(firebase instanceof Map<?, ?> metadata)
                || !(metadata.get("sign_in_provider") instanceof String provider)
                || provider.isBlank()) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_firebase_id_token", "Firebase ID Token claims are invalid", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
