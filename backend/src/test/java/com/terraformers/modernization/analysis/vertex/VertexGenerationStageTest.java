package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.ThinkingLevel;
import org.junit.jupiter.api.Test;

class VertexGenerationStageTest {

    @Test
    void leavesThinkingUnsetByDefaultToPreserveModelDefault() {
        VertexRuntimeProperties properties = new VertexRuntimeProperties();
        VertexGenerationStage stage = stage(properties);

        var config = stage.generationConfig();

        assertThat(config.thinkingConfig()).isEmpty();
        assertThat(config.maxOutputTokens()).contains(8192);
    }

    @Test
    void mapsSupportedThinkingLevelsIntoGenerationConfig() {
        for (ThinkingLevel.Known level : new ThinkingLevel.Known[] {
                ThinkingLevel.Known.LOW,
                ThinkingLevel.Known.MEDIUM,
                ThinkingLevel.Known.HIGH
        }) {
            VertexRuntimeProperties properties = new VertexRuntimeProperties();
            properties.setGenerationThinkingLevel(level.name());

            var config = stage(properties).generationConfig();

            assertThat(config.thinkingConfig()).isPresent();
            assertThat(config.thinkingConfig().orElseThrow().thinkingLevel()).isPresent();
            assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().knownEnum())
                    .isEqualTo(level);
        }
    }

    @Test
    void rejectsUnsupportedThinkingLevelBeforeProviderInvocation() {
        VertexRuntimeProperties properties = new VertexRuntimeProperties();
        properties.setGenerationThinkingLevel("MINIMAL");

        assertThatThrownBy(() -> stage(properties).generationConfig())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generation-thinking-level")
                .hasMessageContaining("LOW, MEDIUM, or HIGH");
    }

    private VertexGenerationStage stage(VertexRuntimeProperties properties) {
        return new VertexGenerationStage(
                null,
                properties,
                new VertexPromptBuilder(),
                new VertexResponseParser(new ObjectMapper())
        );
    }
}
