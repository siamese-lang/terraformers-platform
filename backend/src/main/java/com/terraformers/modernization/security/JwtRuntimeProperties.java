package com.terraformers.modernization.security;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.security.jwt")
public class JwtRuntimeProperties {
    private String provider = "cognito";
    private boolean enabled;
    private String issuerUri;
    private String jwkSetUri;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public boolean isCognito() { return "cognito".equals(provider == null ? null : provider.strip().toLowerCase(Locale.ROOT)); }
    public boolean isFirebase() { return "firebase".equals(provider == null ? null : provider.strip().toLowerCase(Locale.ROOT)); }
}
