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

    private static final String PROVIDER = "test-provider";
    private UserRepository userRepository;
    private JwtExternalIdentityMapper mapper;
    private AuthenticatedUserService service;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mapper = mock(JwtExternalIdentityMapper.class);
        service = new AuthenticatedUserService(userRepository, mapper);
        jwt = Jwt.withTokenValue("token").header("alg", "none").claim("opaque", "value").build();
    }

    @Test
    void returnsExistingUserByProviderAndSubjectWithoutCreatingAnotherUser() {
        map(identity("subject", null, null, "fallback"));
        UserEntity existing = activeUser("subject", "Existing user");
        when(findIdentity("subject")).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreate(jwt)).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsUserFromProviderNeutralIdentity() {
        map(identity("new-subject", null, null, "provider fallback"));
        when(findIdentity("new-subject")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity created = service.getOrCreate(jwt);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertThat(created).isSameAs(captor.getValue());
        assertThat(created.getExternalIdentityProvider()).isEqualTo(PROVIDER);
        assertThat(created.getExternalIdentitySubject()).isEqualTo("new-subject");
        assertThat(created.getDisplayName()).isEqualTo("provider fallback");
        assertThat(created.getRole()).isEqualTo(UserRole.USER);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void rejectsEmailAlreadyLinkedToAnotherExternalIdentity() {
        map(identity("new-subject", "person@example.com", null, "person@example.com"));
        UserEntity other = activeUser("other-subject", "Other user");
        other.setEmail("person@example.com");
        when(findIdentity("new-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.getOrCreate(jwt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authenticated email is already linked to another external identity");
        verify(userRepository, never()).save(any());
    }

    @Test
    void providerFallbackDoesNotOverwriteCustomDisplayName() {
        map(identity("subject", null, null, "provider fallback"));
        UserEntity existing = activeUser("subject", "Custom nickname");
        when(findIdentity("subject")).thenReturn(Optional.of(existing));

        assertThat(service.getOrCreate(jwt).getDisplayName()).isEqualTo("Custom nickname");
        verify(userRepository, never()).save(existing);
    }

    @Test
    void explicitDisplayNameUpdatesExistingUser() {
        map(identity("subject", null, "Identity name", "fallback"));
        UserEntity existing = activeUser("subject", "Old name");
        when(findIdentity("subject")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        assertThat(service.getOrCreate(jwt).getDisplayName()).isEqualTo("Identity name");
    }

    @Test
    void retriesSameProviderAndSubjectLookupAfterConcurrentCreate() {
        map(identity("subject", null, null, "fallback"));
        UserEntity concurrentWinner = activeUser("subject", "Concurrent winner");
        when(findIdentity("subject")).thenReturn(Optional.empty(), Optional.of(concurrentWinner));
        when(userRepository.save(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate external identity"));

        assertThat(service.getOrCreate(jwt)).isSameAs(concurrentWinner);
        verify(userRepository, times(2))
                .findByExternalIdentityProviderAndExternalIdentitySubject(PROVIDER, "subject");
    }

    private void map(AuthenticatedExternalIdentity identity) {
        when(mapper.map(jwt)).thenReturn(identity);
    }

    private AuthenticatedExternalIdentity identity(
            String subject, String email, String explicitName, String fallbackName
    ) {
        return new AuthenticatedExternalIdentity(PROVIDER, subject, email, explicitName, fallbackName);
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
}
