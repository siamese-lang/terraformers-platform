package com.terraformers.modernization.config;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisProviderType;
import com.terraformers.modernization.analysis.EmbeddingProviderType;
import com.terraformers.modernization.analysis.bedrock.BedrockRuntimeProperties;
import com.terraformers.modernization.analysis.ProgressPublisherType;
import com.terraformers.modernization.analysis.sqs.SqsRuntimeProperties;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.security.CognitoJwtRuntimeProperties;
import com.terraformers.modernization.security.JwtRuntimeProperties;
import com.terraformers.modernization.storage.StorageRuntimeProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class RuntimeAdapterContractValidator implements ApplicationRunner {

    private final AnalysisRuntimeProperties properties;
    private final BedrockRuntimeProperties bedrockProperties;
    private final SqsRuntimeProperties sqsProperties;
    private final JwtRuntimeProperties jwtProperties;
    private final CognitoJwtRuntimeProperties cognitoProperties;
    private final StorageRuntimeProperties storageProperties;

    public RuntimeAdapterContractValidator(AnalysisRuntimeProperties properties, BedrockRuntimeProperties bedrockProperties,
            SqsRuntimeProperties sqsProperties, JwtRuntimeProperties jwtProperties,
            CognitoJwtRuntimeProperties cognitoProperties, StorageRuntimeProperties storageProperties) {
        this.properties = properties;
        this.bedrockProperties = bedrockProperties;
        this.sqsProperties = sqsProperties;
        this.jwtProperties = jwtProperties;
        this.cognitoProperties = cognitoProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = findMissingEnabledAdapterSettings();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Enabled runtime adapters are missing required configuration: " + String.join(", ", missing)
            );
        }
    }

    List<String> findMissingEnabledAdapterSettings() {
        List<String> missing = new ArrayList<>();

        if (properties.getMode() == AnalysisMode.EXTERNAL_PYTHON_LEGACY) {
            missing.add("ANALYSIS_MODE_INTEGRATED_JAVA");
        }
        if (jwtProperties.isCognito()) {
            requireText(missing, "COGNITO_USER_POOL_CLIENT_ID", cognitoProperties.getClientId());
        }

        if (properties.resolvedProvider() == AnalysisProviderType.BEDROCK) {
            requireText(missing, "BEDROCK_MODEL_ID", bedrockProperties.getModelId());
        }
        if (properties.getRetrievalMode() != null && properties.getRetrievalMode() != RetrievalMode.DISABLED) {
            if (properties.resolvedProvider() != AnalysisProviderType.BEDROCK) missing.add("ANALYSIS_PROVIDER_BEDROCK");
            if (properties.resolvedEmbeddingProvider() == EmbeddingProviderType.DISABLED) {
                missing.add("EMBEDDING_PROVIDER");
            } else if (properties.resolvedEmbeddingProvider() == EmbeddingProviderType.BEDROCK) {
                requireText(missing, "BEDROCK_EMBEDDING_MODEL_ID", bedrockProperties.getEmbeddingModelId());
            }
            requireText(missing, "OPENSEARCH_ENDPOINT", properties.getOpensearchEndpoint());
            requireText(missing, "INDEX_NAME", properties.getIndexName());
            requireText(missing, "VECTOR_FIELD_NAME", properties.getVectorFieldName());
            requireText(missing, "CONTENT_FIELD_NAME", properties.getContentFieldName());
        }
        if (properties.resolvedProgressPublisher() == ProgressPublisherType.SQS) {
            requireText(missing, "AI_LOG_QUEUE_URL", sqsProperties.getProgressQueueUrl());
            requireText(missing, "TERRAFORM_LOG_QUEUE_URL", sqsProperties.getResultQueueUrl());
        }

        return List.copyOf(missing);
    }

    private void requireText(List<String> missing, String environmentName, String value) {
        if (value == null || value.isBlank()) {
            missing.add(environmentName);
        }
    }
}
