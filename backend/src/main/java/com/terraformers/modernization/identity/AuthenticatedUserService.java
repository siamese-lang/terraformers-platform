package com.terraformers.modernization.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

    private static final String CURRENT_IDENTITY_PROVIDER = "cognito";

    private final UserRepository userRepository;

    public AuthenticatedUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity getOrCreate(Jwt jwt) {
        if (jwt == null) {
            throw new AuthenticationCredentialsNotFoundException("authenticated Cognito JWT is required");
        }

        String externalIdentitySubject = requiredClaim(jwt, "sub", 128);
        String email = optionalClaim(jwt, "email", 320);
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
        }
        String explicitDisplayName = explicitDisplayName(jwt);
        String displayName = explicitDisplayName != null
                ? explicitDisplayName : fallbackDisplayName(jwt, email, externalIdentitySubject);

        String resolvedEmail = email;
        return findByExternalIdentity(externalIdentitySubject)
                .map(existing -> synchronize(existing, resolvedEmail, explicitDisplayName))
                .orElseGet(() -> createWithRetry(externalIdentitySubject, resolvedEmail, displayName));
    }

    private UserEntity synchronize(UserEntity existing, String email, String explicitDisplayName) {
        if (email != null) {
            userRepository.findByEmail(email)
                    .filter(other -> !Objects.equals(other.getUserId(), existing.getUserId()))
                    .ifPresent(other -> {
                        throw new IllegalStateException("authenticated email is already linked to another user");
                    });
        }

        boolean changed = false;
        if (email != null && !email.equals(existing.getEmail())) {
            existing.setEmail(email);
            changed = true;
        }
        // Access tokens commonly provide only a UUID-like cognito:username. Do not
        // let that fallback erase a nickname explicitly saved by the user.
        if (explicitDisplayName != null && !Objects.equals(explicitDisplayName, existing.getDisplayName())) {
            existing.setDisplayName(explicitDisplayName);
            changed = true;
        }
        if (existing.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("authenticated user is not active");
        }
        return changed ? userRepository.save(existing) : existing;
    }

    private UserEntity createWithRetry(String externalIdentitySubject, String email, String displayName) {
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(existing -> {
                throw new IllegalStateException("authenticated email is already linked to another external identity");
            });
        }

        UserEntity user = new UserEntity();
        user.setExternalIdentity(CURRENT_IDENTITY_PROVIDER, externalIdentitySubject);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            return findByExternalIdentity(externalIdentitySubject)
                    .map(existing -> synchronize(existing, email, null))
                    .orElseThrow(() -> exception);
        }
    }

    private Optional<UserEntity> findByExternalIdentity(String externalIdentitySubject) {
        return userRepository.findByExternalIdentityProviderAndExternalIdentitySubject(
                CURRENT_IDENTITY_PROVIDER,
                externalIdentitySubject
        );
    }

    @Transactional
    public UserEntity updateCurrentDisplayName(Jwt jwt, String displayName) {
        UserEntity user = getOrCreate(jwt);
        String normalized = normalizeDisplayName(displayName);
        if (!normalized.equals(user.getDisplayName())) {
            user.setDisplayName(normalized);
            return userRepository.save(user);
        }
        return user;
    }

    private String explicitDisplayName(Jwt jwt) {
        String displayName = firstNonBlankOrNull(
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("nickname")
        );
        return displayName == null ? null : normalizeDisplayName(displayName);
    }

    private String fallbackDisplayName(Jwt jwt, String email, String externalIdentitySubject) {
        String displayName = firstNonBlank(
                email,
                jwt.getClaimAsString("cognito:username"),
                externalIdentitySubject
        );
        return displayName.length() <= 100 ? displayName : displayName.substring(0, 100);
    }

    private String requiredClaim(Jwt jwt, String claimName, int maxLength) {
        String value = optionalClaim(jwt, claimName, maxLength);
        if (value == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "authenticated Cognito JWT is missing required claim: " + claimName
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        throw new IllegalStateException("display name could not be resolved");
    }

    private String firstNonBlankOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        String normalized = displayName.strip();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("displayName exceeds maximum length 100");
        }
        return normalized;
    }
}
