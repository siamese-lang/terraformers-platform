package com.terraformers.modernization.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.bedrock.BedrockRuntimeProperties;
import com.terraformers.modernization.analysis.sqs.SqsRuntimeProperties;
import com.terraformers.modernization.security.CognitoJwtRuntimeProperties;
import com.terraformers.modernization.security.JwtRuntimeProperties;
import com.terraformers.modernization.storage.StorageRuntimeProperties;
import com.terraformers.modernization.reference.RetrievalMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeAdapterContractValidatorTest {

    @Test
    void disabledAdaptersDoNotRequirePlaceholderSettings() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        RuntimeAdapterContractValidator validator = validator(properties, new BedrockRuntimeProperties());

        assertThat(validator.findMissingEnabledAdapterSettings()).isEmpty();
        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void enabledAdaptersReportOnlyTheirMissingSettings() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("bedrock");
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        properties.setEmbeddingProvider("bedrock");
        properties.setProgressPublisher("sqs");
        properties.setOpensearchEndpoint("https://search.example.com");
        properties.setIndexName("terraform-reference");

        RuntimeAdapterContractValidator validator = validator(properties, new BedrockRuntimeProperties());

        assertThat(validator.findMissingEnabledAdapterSettings())
                .containsExactly(
                        "BEDROCK_MODEL_ID",
                        "BEDROCK_EMBEDDING_MODEL_ID",
                        "VECTOR_FIELD_NAME",
                        "CONTENT_FIELD_NAME",
                        "AI_LOG_QUEUE_URL",
                        "TERRAFORM_LOG_QUEUE_URL"
                );
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEDROCK_MODEL_ID")
                .hasMessageContaining("AI_LOG_QUEUE_URL");
    }

    @Test
    void enabledAdaptersPassWhenTheirOwnSettingsArePresent() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("bedrock");
        BedrockRuntimeProperties bedrock = new BedrockRuntimeProperties();
        bedrock.setModelId("bedrock-model");
        bedrock.setEmbeddingModelId("embedding-model");
        properties.setRetrievalMode(RetrievalMode.REQUIRED);
        properties.setOpensearchEndpoint("https://search.example.com");
        properties.setIndexName("terraform-reference");
        properties.setVectorFieldName("vector");
        properties.setContentFieldName("content");
        properties.setEmbeddingProvider("bedrock");
        properties.setProgressPublisher("sqs");

        SqsRuntimeProperties sqs = new SqsRuntimeProperties();
        sqs.setProgressQueueUrl("https://sqs.example.com/progress");
        sqs.setResultQueueUrl("https://sqs.example.com/result");
        RuntimeAdapterContractValidator validator = validator(properties, bedrock, sqs);

        assertThat(validator.findMissingEnabledAdapterSettings()).isEqualTo(List.of());
        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    private RuntimeAdapterContractValidator validator(AnalysisRuntimeProperties properties,
                                                       BedrockRuntimeProperties bedrockProperties) {
        return validator(properties, bedrockProperties, new SqsRuntimeProperties());
    }

    private RuntimeAdapterContractValidator validator(AnalysisRuntimeProperties properties,
                                                       BedrockRuntimeProperties bedrockProperties,
                                                       SqsRuntimeProperties sqsProperties) {
        JwtRuntimeProperties jwt = new JwtRuntimeProperties();
        CognitoJwtRuntimeProperties cognito = new CognitoJwtRuntimeProperties();
        cognito.setClientId("test-client");
        return new RuntimeAdapterContractValidator(properties, bedrockProperties, sqsProperties, jwt, cognito,
                new StorageRuntimeProperties());
    }
}
