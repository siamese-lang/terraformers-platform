package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.bedrock.BedrockAnalysisProvider;
import com.terraformers.modernization.analysis.vertex.VertexAnalysisProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SelectedAnalysisProviderTest {

    @Test
    void delegatesToExplicitlySelectedBedrockAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("bedrock");
        StubAnalysisProvider stub = mock(StubAnalysisProvider.class);
        BedrockAnalysisProvider bedrock = mock(BedrockAnalysisProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BedrockAnalysisProvider> bedrockProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VertexAnalysisProvider> vertexProvider = mock(ObjectProvider.class);
        AnalysisRequestContext context = mock(AnalysisRequestContext.class);
        AnalysisResult expected = mock(AnalysisResult.class);
        when(bedrockProvider.getObject()).thenReturn(bedrock);
        when(bedrock.analyze(context)).thenReturn(expected);

        assertThat(new SelectedAnalysisProvider(properties, stub, bedrockProvider, vertexProvider)
                .analyze(context)).isSameAs(expected);
        verify(bedrock).analyze(context);
        verifyNoInteractions(vertexProvider);
    }

    @Test
    void delegatesToExplicitlySelectedVertexAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("vertex");
        StubAnalysisProvider stub = mock(StubAnalysisProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BedrockAnalysisProvider> bedrockProvider = mock(ObjectProvider.class);
        VertexAnalysisProvider vertex = mock(VertexAnalysisProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VertexAnalysisProvider> vertexProvider = mock(ObjectProvider.class);
        AnalysisRequestContext context = mock(AnalysisRequestContext.class);
        AnalysisResult expected = mock(AnalysisResult.class);
        when(vertexProvider.getObject()).thenReturn(vertex);
        when(vertex.analyze(context)).thenReturn(expected);

        assertThat(new SelectedAnalysisProvider(properties, stub, bedrockProvider, vertexProvider)
                .analyze(context)).isSameAs(expected);
        verify(vertex).analyze(context);
        verifyNoInteractions(bedrockProvider);
    }

    @Test
    void delegatesToStubWithoutResolvingLiveAdapters() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("stub");
        StubAnalysisProvider stub = mock(StubAnalysisProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BedrockAnalysisProvider> bedrockProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VertexAnalysisProvider> vertexProvider = mock(ObjectProvider.class);
        AnalysisRequestContext context = mock(AnalysisRequestContext.class);
        AnalysisResult expected = mock(AnalysisResult.class);
        when(stub.analyze(context)).thenReturn(expected);

        assertThat(new SelectedAnalysisProvider(properties, stub, bedrockProvider, vertexProvider)
                .analyze(context)).isSameAs(expected);
        verify(stub).analyze(context);
        verifyNoInteractions(bedrockProvider, vertexProvider);
    }
}
