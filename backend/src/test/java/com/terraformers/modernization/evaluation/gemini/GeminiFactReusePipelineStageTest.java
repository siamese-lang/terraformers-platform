package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeminiFactReusePipelineStageTest {

    @Test
    void negativeCanonicalClassificationSkipsSecondGenerationCall() {
        AtomicInteger generationCalls = new AtomicInteger();
        GeminiFactReusePipelineStage stage = new GeminiFactReusePipelineStage(
                source -> new GeminiCanonicalEnvelope(
                        AnalysisInputClassification.NON_ARCHITECTURE_IMAGE,
                        0.99,
                        "dashboard screenshot",
                        "",
                        List.of(),
                        List.of(),
                        List.of()
                ),
                (context, envelope, references) -> {
                    generationCalls.incrementAndGet();
                    throw new AssertionError("second generation must be skipped");
                }
        );

        assertThatThrownBy(() -> stage.generate(context(), source(), List.of()))
                .isInstanceOf(AnalysisInputRejectedException.class)
                .satisfies(failure -> assertThat(
                        ((AnalysisInputRejectedException) failure).classification())
                        .isEqualTo(AnalysisInputClassification.NON_ARCHITECTURE_IMAGE));

        assertThat(generationCalls).hasValue(0);
        assertThat(stage.lastTraceOrNull()).isNotNull();
        assertThat(stage.lastTraceOrNull().secondGenerationInvoked()).isFalse();
    }

    @Test
    void architectureCanonicalClassificationInvokesTextGenerationOnce() {
        AtomicInteger generationCalls = new AtomicInteger();
        GeminiFactReusePipelineStage stage = new GeminiFactReusePipelineStage(
                source -> new GeminiCanonicalEnvelope(
                        AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                        0.95,
                        "connected tiers",
                        "Three tier",
                        List.of("ALB", "API"),
                        List.of("ALB -> API"),
                        List.of("aws_lb")
                ),
                (context, envelope, references) -> {
                    generationCalls.incrementAndGet();
                    return new AnalysisGenerationResult(
                            "fake",
                            AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                            envelope.classificationConfidence(),
                            "resource \"aws_lb\" \"main\" {}",
                            envelope.summary(),
                            envelope.components(),
                            envelope.relationships(),
                            List.of(),
                            "STOP",
                            100,
                            false
                    );
                }
        );

        AnalysisGenerationResult result = stage.generate(context(), source(), List.of());

        assertThat(result.inputClassification())
                .isEqualTo(AnalysisInputClassification.ARCHITECTURE_DIAGRAM);
        assertThat(generationCalls).hasValue(1);
        assertThat(stage.lastTraceOrNull().secondGenerationInvoked()).isTrue();
    }

    private AnalysisRequestContext context() {
        return new AnalysisRequestContext(
                "fact-reuse-test", "evaluation", "fixture", "fixture.webp", "case",
                AnalysisMode.INTEGRATED_JAVA);
    }

    private ObjectContent source() {
        byte[] bytes = "fixture".getBytes(StandardCharsets.UTF_8);
        return new ObjectContent(
                new ObjectMetadata("evaluation", "fixture.webp", "image/webp", bytes.length, ""),
                bytes);
    }
}
