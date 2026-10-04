package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.bedrock.BedrockRuntimeProperties;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;

class BedrockArchitectureFactsExtractorTest {
    @Test
    void extractsFactsAndAcceptsJsonFence() throws Exception {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("```json\n{\"summary\":\"EKS workload\",\"components\":[\"EKS\",\"RDS\",\" \"],\"relationships\":[\"EKS connects to RDS\"],\"resourceTypes\":[\"aws_eks_cluster\"]}\n```"));
        ArchitectureRetrievalFacts facts = extractor(client).extract(source());
        assertThat(facts.summary()).isEqualTo("EKS workload");
        assertThat(facts.components()).containsExactly("EKS", "RDS");
        assertThat(facts.relationships()).containsExactly("EKS connects to RDS");
    }

    @Test
    void acceptsFenceVariantsAndCompactJson() throws Exception {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(
                response("```\n{\"summary\":\"bare fence\",\"components\":[\"EKS\"]}\n```"),
                response("```JSON\n{\"summary\":\"uppercase fence\",\"components\":[\"RDS\"]}\n```\nGenerated facts"),
                response("```json{\"summary\":\"compact {facts}\",\"components\":[\"S3\"]}```"));

        assertThat(extractor(client).extract(source()).summary()).isEqualTo("bare fence");
        assertThat(extractor(client).extract(source()).summary()).isEqualTo("uppercase fence");
        assertThat(extractor(client).extract(source()).summary()).isEqualTo("compact {facts}");
    }

    @Test
    void requestsBoundedFactsWithEnoughOutputBudget() throws Exception {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{\"summary\":\"x\",\"components\":[\"EKS\"]}"));

        extractor(client).extract(source());

        ArgumentCaptor<InvokeModelRequest> captor = ArgumentCaptor.forClass(InvokeModelRequest.class);
        verify(client).invokeModel(captor.capture());
        JsonNode request = new ObjectMapper().readTree(captor.getValue().body().asUtf8String());
        assertThat(request.path("max_tokens").asInt()).isEqualTo(800);
        String prompt = request.path("messages").path(0).path("content").path(1).path("text").asText();
        assertThat(prompt).contains("at most 8 strings", "under 60 characters");
    }

    @Test
    void rejectsInvalidCollectionsAndEmptyFactsWithoutLeakingPayload() throws Exception {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{\"summary\":\"x\",\"components\":\"EKS\"}"));
        assertThatThrownBy(() -> extractor(client).extract(source())).hasMessageNotContaining("SENTINEL_IMAGE");
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{\"summary\":\"x\",\"components\":[1]}"));
        assertThatThrownBy(() -> extractor(client).extract(source())).hasMessageNotContaining("SENTINEL_IMAGE");
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{\"summary\":\"\",\"components\":[],\"relationships\":[],\"resourceTypes\":[]}"));
        assertThatThrownBy(() -> extractor(client).extract(source())).hasMessageNotContaining("SENTINEL_IMAGE");
    }

    @Test
    void distinguishesTruncationEmptyThrottlingAndGenericProviderFailure() throws Exception {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class)))
                .thenReturn(raw("{\"stop_reason\":\"max_tokens\",\"content\":[]}"));
        assertThat(failure(client).reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.RESPONSE_TRUNCATED);

        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(raw("{\"content\":[]}"));
        assertThat(failure(client).reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE);

        when(client.invokeModel(any(InvokeModelRequest.class))).thenThrow(
                ThrottlingException.builder().statusCode(429).message("SENTINEL_PROVIDER_PAYLOAD").build());
        ArchitectureFactsExtractionException throttled = failure(client);
        assertThat(throttled.reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.PROVIDER_RATE_LIMITED);
        assertThat(throttled.evaluationDetail()).doesNotContain("SENTINEL_PROVIDER_PAYLOAD");

        when(client.invokeModel(any(InvokeModelRequest.class))).thenThrow(
                new IllegalStateException("SENTINEL_PROVIDER_PAYLOAD"));
        assertThat(failure(client).reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.PROVIDER_ERROR);

        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(raw("   "));
        assertThat(failure(client).reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE);

        when(client.invokeModel(any(InvokeModelRequest.class))).thenThrow(
                ApiCallTimeoutException.builder().message("SENTINEL_PROVIDER_PAYLOAD").build());
        ArchitectureFactsExtractionException timeout = failure(client);
        assertThat(timeout.reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.PROVIDER_TIMEOUT);
        assertThat(timeout.evaluationDetail()).doesNotContain("SENTINEL_PROVIDER_PAYLOAD");
    }

    private ArchitectureFactsExtractionException failure(BedrockRuntimeClient client) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> extractor(client).extract(source()), ArchitectureFactsExtractionException.class);
    }

    private BedrockArchitectureFactsExtractor extractor(BedrockRuntimeClient client) {
        BedrockRuntimeProperties properties = new BedrockRuntimeProperties();
        properties.setModelId("model");
        return new BedrockArchitectureFactsExtractor(client, new ObjectMapper(), properties);
    }
    private ObjectContent source() { return new ObjectContent(new ObjectMetadata("bucket", "key", "image/png", 14, "etag"), "SENTINEL_IMAGE".getBytes()); }
    private InvokeModelResponse response(String facts) throws Exception { return InvokeModelResponse.builder().body(SdkBytes.fromUtf8String(new ObjectMapper().writeValueAsString(java.util.Map.of("content", java.util.List.of(java.util.Map.of("type", "text", "text", facts)))))).build(); }
    private InvokeModelResponse raw(String body) { return InvokeModelResponse.builder().body(SdkBytes.fromUtf8String(body)).build(); }
}
