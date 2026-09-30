package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeminiCurrentImagePipelineStageTest {

    @Test
    void controlPerformsFactsExtractionThenImageGeneration() {
        AtomicInteger factsCalls = new AtomicInteger();
        AtomicInteger generationCalls = new AtomicInteger();
        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(new ObjectMapper());
        GeminiCurrentImagePipelineStage stage = new GeminiCurrentImagePipelineStage(
                source -> {
                    factsCalls.incrementAndGet();
                    return new ArchitectureRetrievalFacts(
                            "three tier", List.of("ALB"), List.of("ALB -> API"), List.of("aws_lb"));
                },
                (context, source, references) -> {
                    generationCalls.incrementAndGet();
                    return new AnalysisGenerationResult(
                            "fake",
                            AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                            0.9,
                            "resource \"aws_lb\" \"main\" {}",
                            "three tier",
                            List.of("ALB"),
                            List.of("ALB -> API"),
                            List.of(),
                            "STOP",
                            100,
                            false
                    );
                },
                telemetry,
                identity("LOW", 800),
                identity("DEFAULT", 8192)
        );

        stage.generate(
                new AnalysisRequestContext(
                        "control-test", "evaluation", "fixture", "fixture.webp", "case",
                        AnalysisMode.INTEGRATED_JAVA),
                source(),
                List.of()
        );

        assertThat(factsCalls).hasValue(1);
        assertThat(generationCalls).hasValue(1);
        assertThat(telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CONTROL))
                .extracting(GeminiLatencyTelemetry.PhaseEvidence::phase)
                .containsExactly(
                        GeminiLatencyTelemetry.Phase.FACT_EXTRACTION,
                        GeminiLatencyTelemetry.Phase.GENERATION);
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
