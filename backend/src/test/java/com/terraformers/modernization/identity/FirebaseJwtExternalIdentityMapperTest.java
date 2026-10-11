package com.terraformers.modernization.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class FirebaseJwtExternalIdentityMapperTest {
    private final FirebaseJwtExternalIdentityMapper mapper = new FirebaseJwtExternalIdentityMapper();

    @Test
    void mapsImmutableUidWithoutEmailLinkingOrRoleInput() {
        AuthenticatedExternalIdentity identity = mapper.map(jwt(Map.of("sub", "uid-123",
                "email", "Person@Example.com", "name", "Person", "role", "ADMIN")));
        assertThat(identity.provider()).isEqualTo("firebase");
        assertThat(identity.subject()).isEqualTo("uid-123");
        assertThat(identity.email()).isEqualTo("person@example.com");
        assertThat(identity.explicitDisplayName()).isEqualTo("Person");
    }

    @Test
    void missingOrOversizedUidFailsClosed() {
        assertThatThrownBy(() -> mapper.map(jwt(Map.of("email", "a@example.com"))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mapper.map(jwt(Map.of("sub", "x".repeat(129)))))
                .isInstanceOf(RuntimeException.class);
    }

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
