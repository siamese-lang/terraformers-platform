package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.analysis.vertex.VertexResponseParser;
import org.junit.jupiter.api.Test;

class LegacyGeminiModelComparisonStageTest {

    @Test
    void preservesFrozenStandardAndCompactTemperatureWithoutThinkingConfig() {
        LegacyGeminiModelComparisonStage stage = new LegacyGeminiModelComparisonStage(
                null,
                "gemini-3.1-pro-preview",
                8192,
                new VertexPromptBuilder(),
                new VertexResponseParser(new ObjectMapper())
        );

        var standard = stage.generationConfig(false);
        var compact = stage.generationConfig(true);

        assertThat(standard.temperature())
                .contains(LegacyGeminiModelComparisonStage.STANDARD_TEMPERATURE);
        assertThat(compact.temperature())
                .contains(LegacyGeminiModelComparisonStage.COMPACT_TEMPERATURE);
        assertThat(standard.maxOutputTokens()).contains(8192);
        assertThat(compact.maxOutputTokens()).contains(8192);
        assertThat(standard.thinkingConfig()).isEmpty();
        assertThat(compact.thinkingConfig()).isEmpty();
    }
}
