package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.ThinkingLevel;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void sensitiveCredentialRecoveryInvokesOnceWithoutCompactFallbackAndMarksRetry() {
        RecordingStage stage = new RecordingStage(false);

        AnalysisGenerationResult result = stage.regenerateAfterSensitiveCredential(
                context(), source(), List.of(reference()));

        assertThat(stage.invocations).containsExactly(new Invocation(false, true, true));
        assertThat(result.retryOccurred()).isTrue();
    }

    @Test
    void normalGenerationRetainsStandardThenCompactTruncationFallback() {
        RecordingStage stage = new RecordingStage(true);

        AnalysisGenerationResult result = stage.generate(context(), source(), List.of(reference()));

        assertThat(stage.invocations).containsExactly(
                new Invocation(false, false, false),
                new Invocation(true, true, false));
        assertThat(result.retryOccurred()).isTrue();
    }

    private VertexGenerationStage stage(VertexRuntimeProperties properties) {
        return new VertexGenerationStage(
                null,
                properties,
                new VertexPromptBuilder(),
                new VertexResponseParser(new ObjectMapper())
        );
    }

    private AnalysisRequestContext context() {
        return new AnalysisRequestContext(
                "job", "project", "bucket", "key.png", "correlation", AnalysisMode.INTEGRATED_JAVA);
    }

    private ObjectContent source() {
        return new ObjectContent(
                new ObjectMetadata("bucket", "key.png", "image/png", 3, "etag"),
                new byte[] {1, 2, 3});
    }

    private ReferenceDocument reference() {
        return new ReferenceDocument("ref", "title", "content", 1.0);
    }

    private record Invocation(boolean compact, boolean retryOccurred, boolean safetyRecovery) {}

    private static final class RecordingStage extends VertexGenerationStage {
        private final List<Invocation> invocations = new ArrayList<>();
        private final boolean truncateFirst;

        private RecordingStage(boolean truncateFirst) {
            super(null, new VertexRuntimeProperties(), new VertexPromptBuilder(),
                    new VertexResponseParser(new ObjectMapper()));
            this.truncateFirst = truncateFirst;
        }

        @Override
        AnalysisGenerationResult invoke(
                ObjectContent source,
                List<ReferenceDocument> references,
                boolean compact,
                boolean retryOccurred,
                boolean sensitiveCredentialRecovery
        ) {
            invocations.add(new Invocation(compact, retryOccurred, sensitiveCredentialRecovery));
            if (truncateFirst && invocations.size() == 1) {
                throw new VertexOutputTruncatedException(8192);
            }
            return new AnalysisGenerationResult(
                    "vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0,
                    "resource \"aws_vpc\" \"main\" { cidr_block = \"10.0.0.0/16\" }",
                    "VPC", List.of("VPC"), List.of(), List.of(), "STOP", 10, retryOccurred);
        }
    }
}
