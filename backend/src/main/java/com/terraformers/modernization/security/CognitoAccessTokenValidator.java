package com.terraformers.modernization.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CognitoAccessTokenValidator implements JwtProviderTokenValidator {

    private final String clientId;

    public CognitoAccessTokenValidator(CognitoJwtRuntimeProperties properties) {
        this.clientId = properties.getClientId();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("token_use"))) {
            return failure("invalid_token_use", "Cognito token_use must be access");
        }
        if (!clientId.equals(jwt.getClaimAsString("client_id"))) {
            return failure("invalid_client", "Cognito access-token client_id does not match configured client id");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult failure(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }
}
