package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SelectedEmbeddingProviderTest {

    @Test
    void delegatesBedrockSelectionToCompatibilityAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setEmbeddingProvider("bedrock");
        BedrockEmbeddingProvider bedrock = mock(BedrockEmbeddingProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BedrockEmbeddingProvider> bedrockProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VertexEmbeddingProvider> vertexProvider = mock(ObjectProvider.class);
        when(bedrockProvider.getObject()).thenReturn(bedrock);
        when(bedrock.embed("query")).thenReturn(List.of(1.0f));

        assertThat(new SelectedEmbeddingProvider(properties, bedrockProvider, vertexProvider)
                .embed("query")).containsExactly(1.0f);
        verify(bedrock).embed("query");
        verifyNoInteractions(vertexProvider);
    }

    @Test
    void delegatesVertexSelectionToTargetAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setEmbeddingProvider("vertex");
        @SuppressWarnings("unchecked")
        ObjectProvider<BedrockEmbeddingProvider> bedrockProvider = mock(ObjectProvider.class);
        VertexEmbeddingProvider vertex = mock(VertexEmbeddingProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VertexEmbeddingProvider> vertexProvider = mock(ObjectProvider.class);
        when(vertexProvider.getObject()).thenReturn(vertex);
        when(vertex.embed("query")).thenReturn(List.of(0.1f, 0.2f));

        assertThat(new SelectedEmbeddingProvider(properties, bedrockProvider, vertexProvider)
                .embed("query")).containsExactly(0.1f, 0.2f);
        verify(vertex).embed("query");
        verifyNoInteractions(bedrockProvider);
    }

    @Test
    void disabledSelectionDoesNotResolveLiveAdapters() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setEmbeddingProvider("disabled");
        @SuppressWarnings("unchecked")
        ObjectProvider<BedrockEmbeddingProvider> bedrockProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<VertexEmbeddingProvider> vertexProvider = mock(ObjectProvider.class);

        assertThatThrownBy(() -> new SelectedEmbeddingProvider(properties, bedrockProvider, vertexProvider)
                .embed("query")).hasMessageContaining("disabled");
        verifyNoInteractions(bedrockProvider, vertexProvider);
    }
}
