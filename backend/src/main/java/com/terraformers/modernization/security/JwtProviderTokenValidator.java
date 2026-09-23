package com.terraformers.modernization.security;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/** Provider adapter boundary for validation beyond standard JWT and issuer checks. */
public interface JwtProviderTokenValidator extends OAuth2TokenValidator<Jwt> {
}
