package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SelectedEmbeddingProviderTest {
    @Test
    void delegatesBedrockSelectionToCompatibilityAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        BedrockEmbeddingProvider bedrock = mock(BedrockEmbeddingProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<BedrockEmbeddingProvider> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bedrock);
        when(bedrock.embed("query")).thenReturn(List.of(1.0f));

        assertThat(new SelectedEmbeddingProvider(properties, provider).embed("query")).containsExactly(1.0f);
        verify(bedrock).embed("query");
    }

    @Test
    void disabledSelectionDoesNotResolveOrInvokeAwsAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setEmbeddingProvider("disabled");
        @SuppressWarnings("unchecked") ObjectProvider<BedrockEmbeddingProvider> provider = mock(ObjectProvider.class);

        assertThatThrownBy(() -> new SelectedEmbeddingProvider(properties, provider).embed("query"))
                .hasMessageContaining("disabled");
    }
}
