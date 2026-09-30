package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.ThinkingLevel;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.analysis.vertex.VertexResponseParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiFactReuseGenerationStageTest {

    @Test
    void candidateGenerationContentIsTextOnlyAndUsesMediumThinking() {
        ObjectMapper mapper = new ObjectMapper();
        GeminiFactReuseGenerationStage stage = new GeminiFactReuseGenerationStage(
                (modelId, content, config) -> {
                    throw new AssertionError("provider invocation is not needed for content contract test");
                },
                "gemini-3.8-flash",
                8192,
                mapper,
                new VertexPromptBuilder(),
                new VertexResponseParser(mapper)
        );
        GeminiCanonicalEnvelope envelope = new GeminiCanonicalEnvelope(
                AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                0.97,
                "connected tiers",
                "ALB routes to API backed by RDS",
                List.of("ALB", "API", "RDS"),
                List.of("ALB -> API", "API -> RDS"),
                List.of("aws_lb", "aws_db_instance")
        );

        var content = stage.buildContent(envelope, List.of(), false);
        var parts = content.parts().orElseThrow();

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).isPresent();
        assertThat(parts.get(0).inlineData()).isEmpty();
        assertThat(parts.get(0).text().orElseThrow())
                .contains("No image is attached", "Canonical image analysis", "aws_db_instance");

        var config = stage.config();
        assertThat(config.maxOutputTokens()).contains(8192);
        assertThat(config.thinkingConfig()).isPresent();
        assertThat(config.thinkingConfig().orElseThrow().thinkingLevel()).isPresent();
        assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().knownEnum())
                .isEqualTo(ThinkingLevel.Known.MEDIUM);
        assertThat(config.temperature()).isEmpty();
    }
}
