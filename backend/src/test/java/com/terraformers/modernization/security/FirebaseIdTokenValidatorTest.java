package com.terraformers.modernization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class FirebaseIdTokenValidatorTest {
    private static final String PROJECT = "terraformers-test";
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT;

    @Test
    void signedFirebaseIdTokenPassesAndInvalidClaimsOrUnsignedTokenFail() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        FirebaseJwtRuntimeProperties properties = new FirebaseJwtRuntimeProperties();
        properties.setProjectId(PROJECT);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), new FirebaseIdTokenValidator(properties)));
        Instant now = Instant.now();
        assertThat(decoder.decode(signed(keys, now, ISSUER, PROJECT, now.plusSeconds(3600))).getSubject())
                .isEqualTo("uid-123");
        for (String badIssuer : List.of("https://example.invalid/", "https://securetoken.google.com/other")) {
            assertThatThrownBy(() -> decoder.decode(signed(keys, now, badIssuer, PROJECT, now.plusSeconds(3600))))
                    .isInstanceOf(JwtException.class);
        }
        assertThatThrownBy(() -> decoder.decode(signed(keys, now, ISSUER, "another-project", now.plusSeconds(3600))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(signed(keys, now.minusSeconds(7200), ISSUER, PROJECT,
                now.minusSeconds(3600)))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(new PlainJWT(claims(now, ISSUER, PROJECT,
                now.plusSeconds(3600))).serialize())).isInstanceOf(JwtException.class);
        KeyPair other = generator.generateKeyPair();
        assertThatThrownBy(() -> decoder.decode(signed(other, now, ISSUER, PROJECT, now.plusSeconds(3600))))
                .isInstanceOf(JwtException.class);
    }

    private String signed(KeyPair keys, Instant issued, String issuer, String audience, Instant expiration)
            throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims(issued, issuer, audience, expiration));
        jwt.sign(new RSASSASigner(keys.getPrivate()));
        return jwt.serialize();
    }

    private JWTClaimsSet claims(Instant issued, String issuer, String audience, Instant expiration) {
        return new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("uid-123")
                .issueTime(Date.from(issued)).expirationTime(Date.from(expiration))
                .claim("auth_time", issued.getEpochSecond())
                .claim("firebase", Map.of("sign_in_provider", "password"))
                .claim("email_verified", true).build();
    }
}
