package com.terraformers.modernization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.identity.UserRepository;
import com.terraformers.modernization.identity.UserRole;
import com.terraformers.modernization.identity.UserStatus;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "terraformers.security.jwt.provider=firebase",
        "terraformers.security.jwt.issuer-uri=https://securetoken.google.com/terraformers-test",
        "terraformers.security.jwt.firebase.project-id=terraformers-test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirebaseSignedBearerIntegrationTest {
    private static final String ISSUER = "https://securetoken.google.com/terraformers-test";
    private static final String PROJECT = "terraformers-test";
    private static final String KEY_ID = "firebase-test-key";
    private static final KeyPair KEY = newKeyPair();
    private static final HttpServer JWKS = startJwksServer();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("terraformers.security.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS.stop(0);
    }

    @Test
    void signedBearerPassesRealDecoderAndMapsUidIntoDatabase() throws Exception {
        String uid = "signed-bearer-admitted";
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + signed(uid, ISSUER, PROJECT, KEY)))
                .andExpect(status().isOk());

        UserEntity mapped = users.findByExternalIdentityProviderAndExternalIdentitySubject("firebase", uid)
                .orElseThrow();
        assertThat(mapped.getUserId()).isNotNull();
        assertThat(mapped.getRole()).isEqualTo(UserRole.USER);
        assertThat(mapped.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void wrongIssuerAudienceAndSignatureAreRejectedBeforeIdentityCreation() throws Exception {
        String uid = "signed-bearer-rejected";
        for (String token : new String[] {
                signed(uid, ISSUER + "/wrong", PROJECT, KEY),
                signed(uid, ISSUER, "other-project", KEY),
                signed(uid, ISSUER, PROJECT, newKeyPair())
        }) {
            mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(users.findByExternalIdentityProviderAndExternalIdentitySubject("firebase", uid)).isEmpty();
    }

    @Test
    void disabledMappedAccountIsForbiddenForTheSameValidBearer() throws Exception {
        String uid = "signed-bearer-disabled";
        UserEntity disabled = new UserEntity();
        disabled.setExternalIdentity("firebase", uid);
        disabled.setDisplayName("Disabled");
        disabled.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(disabled);

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + signed(uid, ISSUER, PROJECT, KEY)))
                .andExpect(status().isForbidden());
        assertThat(users.findByExternalIdentityProviderAndExternalIdentitySubject("firebase", uid))
                .get().extracting(UserEntity::getStatus).isEqualTo(UserStatus.DISABLED);
    }

    private static String signed(String uid, String issuer, String audience, KeyPair key) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(uid)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("auth_time", now.getEpochSecond())
                .claim("firebase", Map.of("sign_in_provider", "password"))
                .claim("email_verified", true).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims);
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RSAKey publicKey = new RSAKey.Builder((RSAPublicKey) KEY.getPublic()).keyID(KEY_ID).build();
            byte[] body = new JWKSet(publicKey).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var response = exchange.getResponseBody()) {
                    response.write(body);
                }
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
