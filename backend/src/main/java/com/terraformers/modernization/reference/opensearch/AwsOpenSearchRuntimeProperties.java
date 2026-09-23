package com.terraformers.modernization.reference.opensearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.aws.opensearch")
public class AwsOpenSearchRuntimeProperties {
    private String signingServiceName = "aoss";

    public String getSigningServiceName() { return signingServiceName; }
    public void setSigningServiceName(String signingServiceName) { this.signingServiceName = signingServiceName; }
}
