package com.terraformers.modernization.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class CognitoAccessTokenValidatorTest {

    private final CognitoAccessTokenValidator validator = new CognitoAccessTokenValidator("expected-client");

    @Test
    void acceptsAccessTokenForConfiguredClient() {
        assertThat(validate(Map.of("token_use", "access", "client_id", "expected-client")).hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingOrIncorrectTokenUse() {
        assertError(validate(Map.of("client_id", "expected-client")), "invalid_token_use");
        assertError(validate(Map.of("token_use", "id", "client_id", "expected-client")), "invalid_token_use");
    }

    @Test
    void rejectsMissingOrMismatchedClientId() {
        assertError(validate(Map.of("token_use", "access")), "invalid_client");
        assertError(validate(Map.of("token_use", "access", "client_id", "other")), "invalid_client");
    }

    private OAuth2TokenValidatorResult validate(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.forEach(builder::claim);
        return validator.validate(builder.build());
    }

    private void assertError(OAuth2TokenValidatorResult result, String code) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).extracting(error -> error.getErrorCode()).containsExactly(code);
    }
}
