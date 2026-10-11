package com.terraformers.modernization.identity;

import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "terraformers.security.jwt.provider", havingValue = "firebase")
public class FirebaseJwtExternalIdentityMapper implements JwtExternalIdentityMapper {
    @Override
    public AuthenticatedExternalIdentity map(Jwt jwt) {
        String uid = jwt.getSubject();
        if (uid == null || uid.isBlank() || uid.length() > 128) {
            throw new AuthenticationCredentialsNotFoundException("Firebase UID is required");
        }
        String email = jwt.getClaimAsString("email");
        if (email != null) {
            email = email.strip().toLowerCase(Locale.ROOT);
            if (email.isBlank() || email.length() > 320) {
                throw new AuthenticationCredentialsNotFoundException("invalid Firebase email claim");
            }
        }
        String name = jwt.getClaimAsString("name");
        if (name != null) {
            name = name.strip();
            if (name.isBlank()) name = null;
            if (name != null && name.length() > 100) {
                throw new AuthenticationCredentialsNotFoundException("invalid Firebase display name");
            }
        }
        String fallback = email == null ? uid : email;
        if (fallback.length() > 100) fallback = fallback.substring(0, 100);
        return new AuthenticatedExternalIdentity("firebase", uid, email, name, fallback);
    }
}
