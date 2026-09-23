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
                        .requestMatchers(HttpMethod.POST, "/api/upload", "/api/analysis/jobs").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/analysis/jobs/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/projects", "/api/project-tree").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/projects/*/visibility").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/users/me/display-name").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/projects/*/terraform/main.tf").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/projects/*/comments", "/api/addProjectComment")
                        .authenticated()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().permitAll())
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
