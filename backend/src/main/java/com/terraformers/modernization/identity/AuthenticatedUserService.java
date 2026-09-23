package com.terraformers.modernization.identity;

import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;
    private final JwtExternalIdentityMapper externalIdentityMapper;

    public AuthenticatedUserService(UserRepository userRepository, JwtExternalIdentityMapper externalIdentityMapper) {
        this.userRepository = userRepository;
        this.externalIdentityMapper = externalIdentityMapper;
    }

    @Transactional
    public UserEntity getOrCreate(Jwt jwt) {
        if (jwt == null) {
            throw new AuthenticationCredentialsNotFoundException("authenticated JWT is required");
        }

        AuthenticatedExternalIdentity identity = externalIdentityMapper.map(jwt);
        String explicitDisplayName = identity.explicitDisplayName() == null
                ? null : normalizeDisplayName(identity.explicitDisplayName());
        String displayName = explicitDisplayName != null
                ? explicitDisplayName : normalizeDisplayName(identity.fallbackDisplayName());
        return findByExternalIdentity(identity)
                .map(existing -> synchronize(existing, identity.email(), explicitDisplayName))
                .orElseGet(() -> createWithRetry(identity, displayName));
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
        // A provider fallback must not erase a display name explicitly saved by the user.
        if (explicitDisplayName != null && !Objects.equals(explicitDisplayName, existing.getDisplayName())) {
            existing.setDisplayName(explicitDisplayName);
            changed = true;
        }
        if (existing.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("authenticated user is not active");
        }
        return changed ? userRepository.save(existing) : existing;
    }

    private UserEntity createWithRetry(AuthenticatedExternalIdentity identity, String displayName) {
        String email = identity.email();
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(existing -> {
                throw new IllegalStateException("authenticated email is already linked to another external identity");
            });
        }

        UserEntity user = new UserEntity();
        user.setExternalIdentity(identity.provider(), identity.subject());
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            return findByExternalIdentity(identity)
                    .map(existing -> synchronize(existing, email, null))
                    .orElseThrow(() -> exception);
        }
    }

    private Optional<UserEntity> findByExternalIdentity(AuthenticatedExternalIdentity identity) {
        return userRepository.findByExternalIdentityProviderAndExternalIdentitySubject(
                identity.provider(),
                identity.subject()
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
