package com.terraformers.modernization.identity;

import org.springframework.security.oauth2.jwt.Jwt;

public interface JwtExternalIdentityMapper {

    AuthenticatedExternalIdentity map(Jwt jwt);
}
