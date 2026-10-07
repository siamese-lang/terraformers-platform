package com.terraformers.modernization.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class JwtResourceServerSecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "terraformers.security.jwt.enabled", havingValue = "true")
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public project handlers still enforce visibility before returning any data.
                        .requestMatchers(HttpMethod.GET, "/api/projects/public", "/api/public-projects",
                                "/api/projects/{projectId:[0-9]+}",
                                "/api/projects/{projectId:[0-9]+}/terraform/main.tf",
                                "/api/projects/{projectId:[0-9]+}/source-image",
                                "/api/projects/{projectId:[0-9]+}/source-object",
                                "/api/projects/{projectId:[0-9]+}/comments",
                                "/api/getProjectComments/{projectId:[0-9]+}",
                                "/api/project-tree/{projectId:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus", "/internal/runtime/required-config")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "terraformers.security.jwt.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localPermitAllSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable());
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "terraformers.security.jwt.enabled", havingValue = "true")
    JwtDecoder jwtDecoder(
            @Value("${terraformers.security.jwt.issuer-uri}") String issuerUri,
            @Value("${terraformers.security.jwt.jwk-set-uri}") String jwkSetUri,
            JwtProviderTokenValidator providerTokenValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                providerTokenValidator
        ));
        return decoder;
    }
}
