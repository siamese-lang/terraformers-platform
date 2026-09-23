package com.terraformers.modernization.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.runtime")
public class RuntimeContractProperties {

    private List<String> requiredEnv = new ArrayList<>(List.of(
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
            "JWT_ISSUER_URI",
            "JWT_JWK_SET_URI",
            "UPLOAD_SOURCE_BUCKET"
    ));

    public List<String> getRequiredEnv() {
        return requiredEnv;
    }

    public void setRequiredEnv(List<String> requiredEnv) {
        this.requiredEnv = requiredEnv;
    }
}
