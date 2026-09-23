package com.terraformers.modernization.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthenticatedUserServiceTest {

    private static final String PROVIDER = "cognito";

    private UserRepository userRepository;
    private AuthenticatedUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AuthenticatedUserService(userRepository);
    }

    @Test
    void returnsExistingUserByProviderAndSubjectWithoutCreatingAnotherUser() {
        UserEntity existing = activeUser("subject", "Existing user");
        when(findIdentity("subject")).thenReturn(Optional.of(existing));

        UserEntity actual = service.getOrCreate(jwt("subject"));

        assertThat(actual).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsUserFromAccessTokenWithoutEmailClaim() {
        Jwt accessToken = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .claim("sub", "access-sub")
                .claim("cognito:username", "terraformers-user")
                .claim("token_use", "access")
                .claim("client_id", "test-client")
                .build();
        when(findIdentity("access-sub")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity created = service.getOrCreate(accessToken);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        verify(userRepository, never()).findByEmail(any());
        assertThat(created).isSameAs(captor.getValue());
        assertThat(created.getExternalIdentityProvider()).isEqualTo(PROVIDER);
        assertThat(created.getExternalIdentitySubject()).isEqualTo("access-sub");
        assertThat(created.getEmail()).isNull();
        assertThat(created.getDisplayName()).isEqualTo("terraformers-user");
        assertThat(created.getRole()).isEqualTo(UserRole.USER);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void subjectFallbackSuppliesDisplayNameWhenTokenHasNoProfileClaims() {
        when(findIdentity("subject-only")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity created = service.getOrCreate(jwt("subject-only"));

        assertThat(created.getDisplayName()).isEqualTo("subject-only");
    }

    @Test
    void uuidOnlyAccessTokenDoesNotOverwriteCustomDisplayName() {
        UserEntity existing = activeUser("subject", "Custom nickname");
        Jwt accessToken = Jwt.withTokenValue("access-token").header("alg", "none")
                .claim("sub", "subject")
                .claim("cognito:username", "123e4567-e89b-12d3-a456-426614174000")
                .build();
        when(findIdentity("subject")).thenReturn(Optional.of(existing));

        UserEntity actual = service.getOrCreate(accessToken);

        assertThat(actual.getDisplayName()).isEqualTo("Custom nickname");
        verify(userRepository, never()).save(existing);
    }

    @Test
    void retriesNeutralIdentityLookupAfterConcurrentCreate() {
        UserEntity concurrentWinner = activeUser("subject", "Concurrent winner");
        when(findIdentity("subject")).thenReturn(Optional.empty(), Optional.of(concurrentWinner));
        when(userRepository.save(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate external identity"));

        UserEntity actual = service.getOrCreate(jwt("subject"));

        assertThat(actual).isSameAs(concurrentWinner);
        verify(userRepository, times(2))
                .findByExternalIdentityProviderAndExternalIdentitySubject(PROVIDER, "subject");
    }

    @Test
    void rejectsEmailAlreadyLinkedToAnotherExternalIdentity() {
        UserEntity other = activeUser("other-subject", "Other user");
        other.setEmail("person@example.com");
        when(findIdentity("new-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(other));
        Jwt token = Jwt.withTokenValue("token").header("alg", "none")
                .claim("sub", "new-subject")
                .claim("email", "Person@Example.com")
                .build();

        assertThatThrownBy(() -> service.getOrCreate(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authenticated email is already linked to another external identity");
        verify(userRepository, never()).save(any());
    }

    private Optional<UserEntity> findIdentity(String subject) {
        return userRepository.findByExternalIdentityProviderAndExternalIdentitySubject(PROVIDER, subject);
    }

    private UserEntity activeUser(String subject, String displayName) {
        UserEntity user = new UserEntity();
        user.setExternalIdentity(PROVIDER, subject);
        user.setDisplayName(displayName);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("token").header("alg", "none").claim("sub", subject).build();
    }
}
