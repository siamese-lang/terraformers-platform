package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.FinishReason;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisProviderTimeoutException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertexGenerationStageTest {

    @Test
    void recognizesOnlyExplicitContentAndSafetyFinishReasonsAsBlocked() {
        assertThat(VertexGenerationStage.isContentBlocked(FinishReason.Known.SAFETY)).isTrue();
        assertThat(VertexGenerationStage.isContentBlocked(FinishReason.Known.BLOCKLIST)).isTrue();
        assertThat(VertexGenerationStage.isContentBlocked(FinishReason.Known.PROHIBITED_CONTENT)).isTrue();
        assertThat(VertexGenerationStage.isContentBlocked(FinishReason.Known.STOP)).isFalse();
        assertThat(VertexGenerationStage.isContentBlocked(FinishReason.Known.OTHER)).isFalse();
        assertThat(VertexGenerationStage.isContentBlocked(FinishReason.Known.MALFORMED_FUNCTION_CALL)).isFalse();
    }

    @Test
    void mapsExplicitProviderCallFailuresWithoutGuessingContentBlock() {
        VertexGenerationStage stage = stage(new VertexRuntimeProperties());

        com.google.genai.errors.ClientException rateLimited =
                org.mockito.Mockito.mock(com.google.genai.errors.ClientException.class);
        org.mockito.Mockito.when(rateLimited.code()).thenReturn(429);

        assertThatThrownBy(() -> { throw stage.providerCallFailure(rateLimited); })
                .isInstanceOfSatisfying(AnalysisProviderFailureException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(AnalysisProviderFailureReason.RATE_LIMITED));

        RuntimeException timeout = new RuntimeException(new HttpTimeoutException("provider timeout"));
        assertThatThrownBy(() -> { throw stage.providerCallFailure(timeout); })
                .isInstanceOf(AnalysisProviderTimeoutException.class);

        IllegalStateException generic = new IllegalStateException("SENTINEL_PROVIDER_PAYLOAD");
        assertThatThrownBy(() -> { throw stage.providerCallFailure(generic); })
                .isInstanceOfSatisfying(AnalysisProviderFailureException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(AnalysisProviderFailureReason.PROVIDER_ERROR))
                .hasCause(generic);
    }

    @Test
    void distinguishesFinishReasonEmptyResponseAndMalformedCompletion() {
        VertexGenerationStage stage = stage(new VertexRuntimeProperties());

        assertThatThrownBy(() -> stage.requireNormalCompletion(
                FinishReason.Known.SAFETY, null, 12))
                .isInstanceOfSatisfying(AnalysisProviderFailureException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(AnalysisProviderFailureReason.CONTENT_BLOCKED));

        assertThatThrownBy(() -> stage.requireNormalCompletion(
                FinishReason.Known.MAX_TOKENS, null, 8192))
                .isInstanceOf(VertexOutputTruncatedException.class);

        assertThatThrownBy(() -> stage.requireNormalCompletion(
                FinishReason.Known.OTHER, null, 12))
                .isInstanceOf(VertexResponseFormatException.class);

        assertThatThrownBy(() -> stage.requireResponseText("  "))
                .isInstanceOfSatisfying(AnalysisProviderFailureException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(AnalysisProviderFailureReason.EMPTY_RESPONSE));

        assertThat(stage.requireResponseText("{\"inputType\":\"ARCHITECTURE_DIAGRAM\"}"))
                .contains("ARCHITECTURE_DIAGRAM");
    }

    @Test
    void sdkPolicyExplicitlyUsesOneAttemptAndNoRetryableStatuses() {
        var http = VertexRuntimeConfiguration.clientHttpOptions();
        assertThat(http.apiVersion()).contains("v1");
        var retry = http.retryOptions().orElseThrow();
        assertThat(retry.attempts()).contains(1);
        assertThat(retry.httpStatusCodes()).contains(List.of());
    }

    @Test
    void requestBudgetsAreTypedPositiveAndCannotExceedApprovedCeilings() {
        VertexRuntimeProperties p = new VertexRuntimeProperties();
        assertThat(p.factsHttpOptions().timeout()).contains(370000);
        assertThat(p.embeddingHttpOptions().timeout()).contains(10000);
        p.setGenerationTimeout(java.time.Duration.ZERO);
        assertThatThrownBy(p::generationHttpOptions).hasMessageContaining("between 1ms and 220s");
        p.setFactsTimeout(java.time.Duration.ofSeconds(371));
        assertThatThrownBy(p::factsHttpOptions).hasMessageContaining("370s");
        p.setEmbeddingTimeout(java.time.Duration.ofSeconds(11));
        assertThatThrownBy(p::embeddingHttpOptions).hasMessageContaining("10s");
    }

    @Test
    void sdkTimeoutIsTranslatedWithoutGenerationOrRepairRetry() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        VertexGenerationStage stage = new VertexGenerationStage(null, new VertexRuntimeProperties(),
                new VertexPromptBuilder(), new VertexResponseParser(new ObjectMapper())) {
            @Override com.google.genai.types.GenerateContentResponse request(com.google.genai.types.Content content,
                    com.google.genai.types.GenerateContentConfig config) {
                calls.incrementAndGet();
                assertThat(config.httpOptions().orElseThrow().timeout()).contains(220000);
                throw new com.google.genai.errors.GenAiIOException(new java.io.InterruptedIOException("timeout"));
            }
        };
        assertThatThrownBy(() -> stage.generate(context(), source(), List.of(),
                new AwsProviderSchemaEvidence(java.util.Map.of())))
                .isInstanceOf(com.terraformers.modernization.analysis.AnalysisProviderTimeoutException.class);
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> stage.repair(facts(), original(), List.of(),
                new AwsProviderSchemaEvidence(java.util.Map.of())))
                .isInstanceOf(com.terraformers.modernization.analysis.AnalysisProviderTimeoutException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void leavesThinkingUnsetByDefaultToPreserveModelDefault() {
        VertexRuntimeProperties properties = new VertexRuntimeProperties();
        VertexGenerationStage stage = stage(properties);

        var config = stage.generationConfig();

        assertThat(config.thinkingConfig()).isEmpty();
        assertThat(config.maxOutputTokens()).contains(8192);
        assertThat(config.httpOptions().orElseThrow().timeout()).contains(220000);
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
    void normalGenerationRetainsStandardThenCompactTruncationFallback() {
        RecordingStage stage = new RecordingStage(1);

        AnalysisGenerationResult result = stage.generate(context(), source(), List.of(reference()));

        assertThat(stage.invocations).containsExactly(
                new Invocation(false, false),
                new Invocation(true, true));
        assertThat(result.retryOccurred()).isTrue();
    }

    @Test
    void repeatedTruncationStopsAfterTwoProviderCalls() {
        RecordingStage stage = new RecordingStage(2);

        assertThatThrownBy(() -> stage.generate(context(), source(), List.of(reference())))
                .isInstanceOf(VertexOutputTruncatedException.class);

        assertThat(stage.invocations).containsExactly(
                new Invocation(false, false),
                new Invocation(true, true));
    }

    @Test
    void bothTruncationCallsReceiveTheGenerationBudget() {
        RepairStage stage = new RepairStage("", FinishReason.Known.MAX_TOKENS);
        assertThatThrownBy(() -> stage.generate(context(), source(), List.of(),
                new AwsProviderSchemaEvidence(java.util.Map.of())))
                .isInstanceOf(VertexOutputTruncatedException.class);
        assertThat(stage.budgets).containsExactly(220000, 220000);
    }

    @Test
    void groundedGenerationRetriesExactlyOnceInCompactModeAfterTruncation() {
        RecordingStage stage = new RecordingStage(1);

        AnalysisGenerationResult result = stage.generate(context(), source(), List.of(reference()),
                new AwsProviderSchemaEvidence(java.util.Map.of("aws_vpc", "cidr_block: string (optional)")));

        assertThat(stage.invocations).containsExactly(
                new Invocation(false, false),
                new Invocation(true, true));
        assertThat(result.retryOccurred()).isTrue();
    }

    @Test
    void repeatedGroundedGenerationTruncationStopsAfterTwoCalls() {
        RecordingStage stage = new RecordingStage(2);

        assertThatThrownBy(() -> stage.generate(context(), source(), List.of(reference()),
                new AwsProviderSchemaEvidence(java.util.Map.of())))
                .isInstanceOf(VertexOutputTruncatedException.class);

        assertThat(stage.invocations).containsExactly(
                new Invocation(false, false),
                new Invocation(true, true));
    }

    @Test
    void groundedGenerationDoesNotRetryNonTruncationFailures() {
        for (FinishReason.Known reason : List.of(FinishReason.Known.SAFETY,
                FinishReason.Known.OTHER, FinishReason.Known.STOP)) {
            RepairStage stage = new RepairStage("not-json", reason);

            assertThatThrownBy(() -> stage.generate(context(), source(), List.of(reference()),
                    new AwsProviderSchemaEvidence(java.util.Map.of())))
                    .isInstanceOf(reason == FinishReason.Known.SAFETY
                            ? AnalysisProviderFailureException.class : VertexResponseFormatException.class);

            assertThat(stage.calls).isEqualTo(1);
        }
    }

    @Test
    void repairMakesOneTextOnlyCallAndReturnsOnlyCorrectedTerraform() {
        RepairStage stage = new RepairStage("{\"terraformCode\":\"corrected HCL\"}", FinishReason.Known.STOP);
        assertThat(stage.repair(facts(), original(), List.of(reference()),
                new AwsProviderSchemaEvidence(java.util.Map.of()))).isEqualTo("corrected HCL");
        assertThat(stage.calls).isEqualTo(1);
        assertThat(stage.budgets).containsExactly(220000);
        assertThat(stage.config.maxOutputTokens()).contains(16_384);
        assertThat(stage.config.responseJsonSchema()).contains(new VertexPromptBuilder().repairResponseJsonSchema());
        assertThat(stage.config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().knownEnum())
                .isEqualTo(ThinkingLevel.Known.LOW);
        assertThat(stage.generationConfig().maxOutputTokens()).contains(8192);
        assertThat(stage.generationConfig().thinkingConfig()).isEmpty();
        assertThat(stage.content.parts().orElseThrow()).hasSize(1);
        assertThat(stage.content.parts().orElseThrow().get(0).inlineData()).isEmpty();
        assertThat(stage.content.parts().orElseThrow().get(0).text()).get()
                .asString().contains("Prior Terraform draft", original().terraformCode());
    }

    @Test
    void repairTruncationMalformedResponseAndReclassificationNeverRetry() {
        RepairStage truncated = new RepairStage("{\"terraformCode\":\"apparently complete HCL\"}", FinishReason.Known.MAX_TOKENS);
        assertThatThrownBy(() -> truncated.repair(facts(), original(), List.of(),
                new AwsProviderSchemaEvidence(java.util.Map.of()))).isInstanceOf(VertexOutputTruncatedException.class);
        assertThat(truncated.calls).isEqualTo(1);
        for (String invalid : List.of("not-json", "{}", "{\"terraformCode\":\"\"}",
                "{\"terraformCode\":\"HCL\",\"inputType\":\"AMBIGUOUS\"}")) {
            RepairStage stage = new RepairStage(invalid, FinishReason.Known.STOP);
            assertThatThrownBy(() -> stage.repair(facts(), original(), List.of(),
                    new AwsProviderSchemaEvidence(java.util.Map.of()))).isInstanceOf(VertexResponseFormatException.class);
            assertThat(stage.calls).isEqualTo(1);
        }
    }

    @Test
    void repairProviderFailuresRemainSingleAttemptAndKeepTypedReasons() {
        var rateLimited = org.mockito.Mockito.mock(com.google.genai.errors.ClientException.class);
        org.mockito.Mockito.when(rateLimited.code()).thenReturn(429);
        for (RuntimeException failure : List.of(rateLimited, new IllegalStateException("SENTINEL_SECRET_PAYLOAD"))) {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var stage = new VertexGenerationStage(null, new VertexRuntimeProperties(), new VertexPromptBuilder(),
                    new VertexResponseParser(new ObjectMapper())) {
                @Override com.google.genai.types.GenerateContentResponse request(com.google.genai.types.Content content,
                        com.google.genai.types.GenerateContentConfig config) {
                    calls.incrementAndGet();
                    throw failure;
                }
            };
            assertThatThrownBy(() -> stage.repair(facts(), original(), List.of(), new AwsProviderSchemaEvidence(java.util.Map.of())))
                    .isInstanceOfSatisfying(AnalysisProviderFailureException.class, result -> assertThat(result.reason())
                            .isEqualTo(failure == rateLimited ? AnalysisProviderFailureReason.RATE_LIMITED
                                    : AnalysisProviderFailureReason.PROVIDER_ERROR));
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void providerTelemetryDistinguishesInitialCompactAndRepairWithoutResponseOrPrompt() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(VertexGenerationStage.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var stage = new RepairStage("SENTINEL_RESPONSE_SECRET", FinishReason.Known.MAX_TOKENS);
            assertThatThrownBy(() -> stage.generate(context(), source(), List.of()))
                    .isInstanceOf(VertexOutputTruncatedException.class);
            assertThatThrownBy(() -> stage.repair(facts(), original(), List.of(), new AwsProviderSchemaEvidence(java.util.Map.of())))
                    .isInstanceOf(VertexOutputTruncatedException.class);
            assertThat(appender.list).hasSize(3);
            String messages = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(messages).contains("stage=initial_generation compact=false", "stage=initial_generation compact=true",
                    "stage=repair compact=false", "finishReason=MAX_TOKENS", "maxOutputTokens=8192", "maxOutputTokens=16384",
                    "outputTokens=123", "thinkingTokens=45", "totalTokens=168")
                    .doesNotContain("SENTINEL_RESPONSE_SECRET", "Prior Terraform draft", original().terraformCode());
        } finally {
            logger.detachAppender(appender); appender.stop();
        }
    }

    private com.terraformers.modernization.reference.ArchitectureRetrievalFacts facts() {
        return new com.terraformers.modernization.reference.ArchitectureRetrievalFacts("VPC", List.of("VPC"),
                List.of("VPC -> app"), List.of("aws_vpc"));
    }

    private AnalysisGenerationResult original() {
        return new AnalysisGenerationResult("vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                1.0, "resource \"aws_vpc\" \"main\" {}", "VPC", List.of("VPC"), List.of(), List.of(), "STOP", 10, false);
    }

    private static final class RepairStage extends VertexGenerationStage {
        private int calls;
        private final List<Integer> budgets = new java.util.ArrayList<>();
        private com.google.genai.types.Content content;
        private com.google.genai.types.GenerateContentConfig config;
        private final String text;
        private final FinishReason.Known reason;
        private RepairStage(String text, FinishReason.Known reason) {
            super(null, new VertexRuntimeProperties(), new VertexPromptBuilder(), new VertexResponseParser(new ObjectMapper()));
            this.text = text;
            this.reason = reason;
        }
        @Override
        com.google.genai.types.GenerateContentResponse request(com.google.genai.types.Content content,
                com.google.genai.types.GenerateContentConfig config) {
            calls++;
            budgets.add(config.httpOptions().orElseThrow().timeout().orElseThrow());
            this.content = content;
            this.config = config;
            return com.google.genai.types.GenerateContentResponse.builder().candidates(
                    com.google.genai.types.Candidate.builder().finishReason(reason).content(
                            com.google.genai.types.Content.fromParts(com.google.genai.types.Part.fromText(text))))
                    .usageMetadata(com.google.genai.types.GenerateContentResponseUsageMetadata.builder()
                            .candidatesTokenCount(123).thoughtsTokenCount(45).totalTokenCount(168))
                    .build();
        }
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

    private record Invocation(boolean compact, boolean retryOccurred) {}

    private static final class RecordingStage extends VertexGenerationStage {
        private final List<Invocation> invocations = new ArrayList<>();
        private int truncationsRemaining;

        private RecordingStage(int truncationsRemaining) {
            super(null, new VertexRuntimeProperties(), new VertexPromptBuilder(),
                    new VertexResponseParser(new ObjectMapper()));
            this.truncationsRemaining = truncationsRemaining;
        }

        @Override
        AnalysisGenerationResult invoke(
                ObjectContent source,
                List<ReferenceDocument> references,
                AwsProviderSchemaEvidence schemaEvidence,
                boolean compact,
                boolean retryOccurred
        ) {
            invocations.add(new Invocation(compact, retryOccurred));
            if (truncationsRemaining > 0) {
                truncationsRemaining--;
                throw new VertexOutputTruncatedException(8192);
            }
            return new AnalysisGenerationResult(
                    "vertex:test", AnalysisInputClassification.ARCHITECTURE_DIAGRAM, 1.0,
                    "resource \"aws_vpc\" \"main\" { cidr_block = \"10.0.0.0/16\" }",
                    "VPC", List.of("VPC"), List.of(), List.of(), "STOP", 10, retryOccurred);
        }
    }
}
