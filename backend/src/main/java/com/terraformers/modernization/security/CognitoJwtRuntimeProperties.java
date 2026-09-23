package com.terraformers.modernization.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.security.jwt.cognito")
public class CognitoJwtRuntimeProperties {
    private String clientId = "";
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
}
