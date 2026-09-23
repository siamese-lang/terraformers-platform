package com.terraformers.modernization;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.bedrock.BedrockRuntimeProperties;
import com.terraformers.modernization.analysis.sqs.SqsRuntimeProperties;
import com.terraformers.modernization.config.RuntimeContractProperties;
import com.terraformers.modernization.reference.opensearch.AwsOpenSearchRuntimeProperties;
import com.terraformers.modernization.security.CognitoJwtRuntimeProperties;
import com.terraformers.modernization.security.JwtRuntimeProperties;
import com.terraformers.modernization.storage.StorageRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RuntimeContractProperties.class, AnalysisRuntimeProperties.class, BedrockRuntimeProperties.class,
        SqsRuntimeProperties.class, AwsOpenSearchRuntimeProperties.class, JwtRuntimeProperties.class,
        CognitoJwtRuntimeProperties.class, StorageRuntimeProperties.class})
public class TerraformersBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerraformersBackendApplication.class, args);
    }
}
