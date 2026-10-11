package com.terraformers.modernization.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.security.jwt.firebase")
public class FirebaseJwtRuntimeProperties {
    public static final String JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private String projectId = "";
    private String billableUids = "";

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getBillableUids() { return billableUids; }
    public void setBillableUids(String billableUids) { this.billableUids = billableUids; }
}
