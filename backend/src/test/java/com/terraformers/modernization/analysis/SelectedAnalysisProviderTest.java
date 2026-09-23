package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.bedrock.BedrockAnalysisProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SelectedAnalysisProviderTest {
    @Test
    void delegatesToExplicitlySelectedBedrockAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("bedrock");
        StubAnalysisProvider stub = mock(StubAnalysisProvider.class);
        BedrockAnalysisProvider bedrock = mock(BedrockAnalysisProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<BedrockAnalysisProvider> provider = mock(ObjectProvider.class);
        AnalysisRequestContext context = mock(AnalysisRequestContext.class);
        AnalysisResult expected = mock(AnalysisResult.class);
        when(provider.getObject()).thenReturn(bedrock);
        when(bedrock.analyze(context)).thenReturn(expected);

        assertThat(new SelectedAnalysisProvider(properties, stub, provider).analyze(context)).isSameAs(expected);
        verify(bedrock).analyze(context);
    }

    @Test
    void delegatesToStubWithoutResolvingBedrockAdapter() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("stub");
        StubAnalysisProvider stub = mock(StubAnalysisProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<BedrockAnalysisProvider> provider = mock(ObjectProvider.class);
        AnalysisRequestContext context = mock(AnalysisRequestContext.class);
        AnalysisResult expected = mock(AnalysisResult.class);
        when(stub.analyze(context)).thenReturn(expected);

        assertThat(new SelectedAnalysisProvider(properties, stub, provider).analyze(context)).isSameAs(expected);
        verify(stub).analyze(context);
    }
}
