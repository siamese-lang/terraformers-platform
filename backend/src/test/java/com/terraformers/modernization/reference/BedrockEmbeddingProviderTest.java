package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.bedrock.BedrockRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

class BedrockEmbeddingProviderTest {
    @Test
    void invokesConfiguredEmbeddingModel() {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{\"embedding\":[1,2]}"));

        new BedrockEmbeddingProvider(client, new ObjectMapper(), properties(), bedrockProperties()).embed("query");

        ArgumentCaptor<InvokeModelRequest> request = ArgumentCaptor.forClass(InvokeModelRequest.class);
        verify(client).invokeModel(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().modelId()).isEqualTo("embedding-model");
    }

    @Test
    void rejectsMissingOrEmptyEmbeddingArraysAndDimensionMismatch() {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        AnalysisRuntimeProperties properties = properties();
        BedrockEmbeddingProvider provider = new BedrockEmbeddingProvider(client, new ObjectMapper(), properties, bedrockProperties());
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{}"));
        assertThatThrownBy(() -> provider.embed("sentinel prompt")).hasMessageContaining("failed to generate");
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{\"embedding\":[]}"));
        assertThatThrownBy(() -> provider.embed("sentinel prompt")).hasMessageContaining("failed to generate");
        properties.setExpectedVectorDimension(3);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(response("{\"embedding\":[1,2]}"));
        assertThatThrownBy(() -> provider.embed("sentinel prompt")).hasMessageContaining("failed to generate");
    }

    @Test
    void wrapsBedrockInvocationFailureWithoutEmbeddingInput() {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.invokeModel(any(InvokeModelRequest.class))).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> new BedrockEmbeddingProvider(client, new ObjectMapper(), properties(), bedrockProperties()).embed("sentinel prompt"))
                .hasMessageNotContaining("sentinel prompt");
    }

    private AnalysisRuntimeProperties properties() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        return properties;
    }

    private BedrockRuntimeProperties bedrockProperties() {
        BedrockRuntimeProperties properties = new BedrockRuntimeProperties();
        properties.setEmbeddingModelId("embedding-model");
        return properties;
    }

    private InvokeModelResponse response(String body) {
        return InvokeModelResponse.builder().body(SdkBytes.fromUtf8String(body)).build();
    }
}
