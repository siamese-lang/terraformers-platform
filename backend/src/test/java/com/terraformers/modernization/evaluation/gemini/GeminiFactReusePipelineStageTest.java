package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(new ObjectMapper());
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
                },
                telemetry,
                identity("LOW", 1200),
                identity("MEDIUM", 8192)
        );

        assertThatThrownBy(() -> stage.generate(context(), source(), List.of()))
                .isInstanceOf(AnalysisInputRejectedException.class)
                .satisfies(failure -> assertThat(
                        ((AnalysisInputRejectedException) failure).classification())
                        .isEqualTo(AnalysisInputClassification.NON_ARCHITECTURE_IMAGE));

        assertThat(generationCalls).hasValue(0);
        assertThat(stage.lastTraceOrNull()).isNotNull();
        assertThat(stage.lastTraceOrNull().secondGenerationInvoked()).isFalse();
        assertThat(telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CANDIDATE))
                .extracting(GeminiLatencyTelemetry.PhaseEvidence::phase)
                .containsExactly(GeminiLatencyTelemetry.Phase.CANONICAL_EXTRACTION);
    }

    @Test
    void architectureCanonicalClassificationInvokesTextGenerationOnce() {
        AtomicInteger generationCalls = new AtomicInteger();
        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(new ObjectMapper());
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
                },
                telemetry,
                identity("LOW", 1200),
                identity("MEDIUM", 8192)
        );

        AnalysisGenerationResult result = stage.generate(context(), source(), List.of());

        assertThat(result.inputClassification())
                .isEqualTo(AnalysisInputClassification.ARCHITECTURE_DIAGRAM);
        assertThat(generationCalls).hasValue(1);
        assertThat(stage.lastTraceOrNull().secondGenerationInvoked()).isTrue();
        assertThat(telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CANDIDATE))
                .extracting(GeminiLatencyTelemetry.PhaseEvidence::phase)
                .containsExactly(
                        GeminiLatencyTelemetry.Phase.CANONICAL_EXTRACTION,
                        GeminiLatencyTelemetry.Phase.GENERATION);
    }

    @Test
    void providerFailureStillCompletesFailedPhaseEvidence() {
        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(new ObjectMapper());
        GeminiFactReusePipelineStage stage = new GeminiFactReusePipelineStage(
                source -> { throw new IllegalStateException("sensitive provider detail"); },
                (context, envelope, references) -> { throw new AssertionError("must not generate"); },
                telemetry,
                identity("LOW", 1200),
                identity("MEDIUM", 8192));

        assertThatThrownBy(() -> stage.generate(context(), source(), List.of()))
                .isInstanceOf(IllegalStateException.class);

        var phase = telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CANDIDATE).get(0);
        assertThat(phase.succeeded()).isFalse();
        assertThat(phase.errorType()).isEqualTo("IllegalStateException");
        assertThat(phase.toString()).doesNotContain("sensitive provider detail");
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

    private GeminiLatencyTelemetry.RequestIdentity identity(String thinking, int maxTokens) {
        return new GeminiLatencyTelemetry.RequestIdentity("gemini-3.8-flash", "global", thinking, maxTokens);
    }
}
