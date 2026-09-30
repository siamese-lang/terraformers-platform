package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.ThinkingLevel;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GeminiCanonicalEnvelopeExtractorTest {

    @Test
    void usesOneImagePartAndLowThinkingForBoundedCanonicalEnvelope() {
        GeminiCanonicalEnvelopeExtractor extractor = new GeminiCanonicalEnvelopeExtractor(
                (modelId, content, config) -> {
                    assertThat(modelId).isEqualTo("gemini-3.8-flash");
                    assertThat(config.maxOutputTokens())
                            .contains(GeminiCanonicalEnvelopeExtractor.MAX_OUTPUT_TOKENS);
                    assertThat(config.thinkingConfig()).isPresent();
                    assertThat(config.thinkingConfig().orElseThrow().thinkingLevel()).isPresent();
                    assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().knownEnum())
                            .isEqualTo(ThinkingLevel.Known.LOW);
                    assertThat(content.parts()).isPresent();
                    var parts = content.parts().orElseThrow();
                    assertThat(parts).hasSize(2);
                    assertThat(parts.stream().filter(part -> part.inlineData().isPresent()).count()).isEqualTo(1);
                    assertThat(parts.stream().filter(part -> part.text().isPresent()).count()).isEqualTo(1);
                    return new GeminiCanonicalEnvelopeExtractor.EnvelopeResponse("""
                            {"inputType":"ARCHITECTURE_DIAGRAM",
                             "classificationConfidence":0.98,
                             "classificationReason":"connected cloud tiers",
                             "summary":"Three tier architecture",
                             "components":["ALB","API","RDS"],
                             "relationships":["ALB -> API","API -> RDS"],
                             "resourceTypes":["aws_lb","aws_db_instance"]}
                            """, false);
                },
                new ObjectMapper(),
                "gemini-3.8-flash"
        );

        GeminiCanonicalEnvelope envelope = extractor.extract(source());

        assertThat(envelope.inputType()).isEqualTo(AnalysisInputClassification.ARCHITECTURE_DIAGRAM);
        assertThat(envelope.summary()).isEqualTo("Three tier architecture");
        assertThat(envelope.resourceTypes()).containsExactly("aws_lb", "aws_db_instance");
    }

    @Test
    void rejectsNonArchitectureEnvelopeThatLeaksArchitectureFacts() {
        GeminiCanonicalEnvelopeExtractor extractor = new GeminiCanonicalEnvelopeExtractor(
                (modelId, content, config) -> new GeminiCanonicalEnvelopeExtractor.EnvelopeResponse("""
                        {"inputType":"AMBIGUOUS",
                         "classificationConfidence":0.6,
                         "classificationReason":"cropped",
                         "summary":"should be empty",
                         "components":[],"relationships":[],"resourceTypes":[]}
                        """, false),
                new ObjectMapper(),
                "gemini-3.8-flash"
        );

        assertThatThrownBy(() -> extractor.extract(source()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain architecture facts");
    }

    private ObjectContent source() {
        byte[] bytes = "fixture-image".getBytes(StandardCharsets.UTF_8);
        return new ObjectContent(
                new ObjectMetadata("evaluation", "fixture.webp", "image/webp", bytes.length, ""),
                bytes
        );
    }
}
