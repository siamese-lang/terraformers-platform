package com.terraformers.modernization.identity;

import java.util.Locale;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CognitoJwtExternalIdentityMapper implements JwtExternalIdentityMapper {

    static final String PROVIDER = "cognito";

    @Override
    public AuthenticatedExternalIdentity map(Jwt jwt) {
        String subject = requiredClaim(jwt, "sub", 128);
        String email = optionalClaim(jwt, "email", 320);
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
        }
        String explicitDisplayName = explicitDisplayName(jwt);
        String fallbackDisplayName = firstNonBlank(email, jwt.getClaimAsString("cognito:username"), subject);
        if (fallbackDisplayName.length() > 100) {
            fallbackDisplayName = fallbackDisplayName.substring(0, 100);
        }
        return new AuthenticatedExternalIdentity(
                PROVIDER, subject, email, explicitDisplayName, fallbackDisplayName
        );
    }

    private String requiredClaim(Jwt jwt, String claimName, int maxLength) {
        String value = optionalClaim(jwt, claimName, maxLength);
        if (value == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "authenticated JWT is missing required claim: " + claimName
            );
        }
        return value;
    }

    private String optionalClaim(Jwt jwt, String claimName, int maxLength) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(claimName + " exceeds maximum length " + maxLength);
        }
        return normalized;
    }

    private String explicitDisplayName(Jwt jwt) {
        for (String claimName : new String[] {"name", "preferred_username", "nickname"}) {
            String value = jwt.getClaimAsString(claimName);
            if (value != null && !value.isBlank()) {
                String normalized = value.strip();
                if (normalized.length() > 100) {
                    throw new IllegalArgumentException(claimName + " exceeds maximum length 100");
                }
                return normalized;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        throw new IllegalStateException("display name could not be resolved");
    }

}
