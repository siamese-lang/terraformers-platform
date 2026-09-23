package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terraformers.modernization.analysis.bedrock.BedrockRuntimeProperties;

import com.terraformers.modernization.reference.RetrievalMode;

import org.junit.jupiter.api.Test;

class AnalysisRuntimePropertiesTest {

    @Test
    void bedrockPropertiesDefaultOutputLimitTo8192AndAllowOverride() {
        BedrockRuntimeProperties properties = new BedrockRuntimeProperties();

        assertThat(properties.getMaxTokens()).isEqualTo(8192);

        properties.setMaxTokens(4096);
        assertThat(properties.getMaxTokens()).isEqualTo(4096);
    }

    @Test
    void defaultsRetrievalToDisabled() {
        assertThat(new AnalysisRuntimeProperties().getRetrievalMode()).isEqualTo(RetrievalMode.DISABLED);
    }

    @Test
    void resolvesExplicitAnalysisAndEmbeddingProviders() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider(" stub ");
        assertThat(properties.resolvedProvider()).isEqualTo(AnalysisProviderType.STUB);

        properties.setProvider("BEDROCK");
        assertThat(properties.resolvedProvider()).isEqualTo(AnalysisProviderType.BEDROCK);
        assertThat(new AnalysisRuntimeProperties().resolvedEmbeddingProvider()).isEqualTo(EmbeddingProviderType.DISABLED);
    }

    @Test
    void rejectsUnknownProviderSelectors() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setProvider("unknown");
        assertThatThrownBy(properties::resolvedProvider).hasMessageContaining("Unsupported terraformers.analysis.provider");
        properties.setEmbeddingProvider("unknown");
        assertThatThrownBy(properties::resolvedEmbeddingProvider)
                .hasMessageContaining("Unsupported terraformers.analysis.embedding-provider");
        properties.setProgressPublisher("unknown");
        assertThatThrownBy(properties::resolvedProgressPublisher)
                .hasMessageContaining("Unsupported terraformers.analysis.progress-publisher");
    }
}
