package com.terraformers.modernization.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoJwtExternalIdentityMapperTest {

    private final CognitoJwtExternalIdentityMapper mapper = new CognitoJwtExternalIdentityMapper();

    @Test
    void mapsProviderSubjectEmailAndExplicitNameInPrecedenceOrder() {
        AuthenticatedExternalIdentity identity = mapper.map(jwt(Map.of(
                "sub", "subject", "email", "Person@Example.com", "name", "Name",
                "preferred_username", "preferred", "nickname", "nickname"
        )));

        assertThat(identity.provider()).isEqualTo("cognito");
        assertThat(identity.subject()).isEqualTo("subject");
        assertThat(identity.email()).isEqualTo("person@example.com");
        assertThat(identity.explicitDisplayName()).isEqualTo("Name");
        assertThat(identity.fallbackDisplayName()).isEqualTo("person@example.com");
    }

    @Test
    void usesPreferredUsernameThenNicknameForExplicitName() {
        assertThat(mapper.map(jwt(Map.of(
                "sub", "subject", "preferred_username", "preferred", "nickname", "nickname"
        ))).explicitDisplayName()).isEqualTo("preferred");
        assertThat(mapper.map(jwt(Map.of("sub", "subject", "nickname", "nickname")))
                .explicitDisplayName()).isEqualTo("nickname");
    }

    @Test
    void usesCognitoUsernameThenSubjectAsFallback() {
        assertThat(mapper.map(jwt(Map.of("sub", "subject", "cognito:username", "username")))
                .fallbackDisplayName()).isEqualTo("username");
        assertThat(mapper.map(jwt(Map.of("sub", "subject"))).fallbackDisplayName()).isEqualTo("subject");
    }

    @Test
    void rejectsMissingOrBlankSubject() {
        assertThatThrownBy(() -> mapper.map(jwt(Map.of())))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> mapper.map(jwt(Map.of("sub", "  "))))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
