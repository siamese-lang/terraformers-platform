package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AnalysisObservabilityTest {
    @Test
    void classifiesProviderNeutralFailuresAndPublishesTheirMetricTags() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AnalysisObservability observability = new AnalysisObservability(registry);
        AnalysisProviderFailureException truncated = providerFailure(AnalysisProviderFailureReason.OUTPUT_TRUNCATED);
        AnalysisProviderFailureException rejected = providerFailure(AnalysisProviderFailureReason.INPUT_REJECTED);
        AnalysisProviderFailureException format = providerFailure(AnalysisProviderFailureReason.RESPONSE_FORMAT);
        AnalysisProviderTimeoutException timeout = new AnalysisProviderTimeoutException(new RuntimeException());

        assertThat(observability.category(truncated)).isEqualTo("truncated_output");
        assertThat(observability.category(rejected)).isEqualTo("rejected_input");
        assertThat(observability.category(format)).isEqualTo("response_format");
        assertThat(observability.category(timeout)).isEqualTo("timeout");
        assertThat(observability.category(new IllegalStateException())).isEqualTo("other");

        observability.jobFailed(truncated);
        observability.jobFailed(rejected);
        observability.jobFailed(format);
        observability.jobFailed(timeout);

        assertThat(failureCount(registry, "truncated_output")).isEqualTo(1);
        assertThat(failureCount(registry, "rejected_input")).isEqualTo(1);
        assertThat(failureCount(registry, "response_format")).isEqualTo(1);
        assertThat(failureCount(registry, "timeout")).isEqualTo(1);
    }


    @Test
    void classifiesGoogleProvider429AcrossCauseChainAndPublishesExistingMetricTags() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AnalysisObservability observability = new AnalysisObservability(registry);
        com.google.genai.errors.ClientException rateLimited = mock(com.google.genai.errors.ClientException.class);
        when(rateLimited.code()).thenReturn(429);
        RuntimeException wrapped = new IllegalStateException("bounded wrapper", rateLimited);

        assertThat(observability.category(wrapped)).isEqualTo("provider_rate_limited");
        observability.jobFailed(wrapped);
        assertThat(failureCount(registry, "provider_rate_limited")).isEqualTo(1);

        try {
            observability.recordStage(AnalysisTelemetryStage.ANALYSIS_EXECUTION, () -> {
                throw wrapped;
            });
        } catch (IllegalStateException ignored) {
        }
        assertThat(registry.find("terraformers.analysis.stage.failures")
                .tag("stage", AnalysisTelemetryStage.ANALYSIS_EXECUTION.tag())
                .tag("category", "provider_rate_limited").counter().count()).isEqualTo(1);
    }

    @Test
    void doesNotClassifyNon429ProviderRuntimeAsRateLimited() {
        AnalysisObservability observability = new AnalysisObservability(
                new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
        com.google.genai.errors.ClientException unavailable = mock(com.google.genai.errors.ClientException.class);
        when(unavailable.code()).thenReturn(503);

        assertThat(observability.category(new RuntimeException(unavailable))).isEqualTo("other");
    }

    @Test
    void publishesStableTerraformFailureCategoriesWithoutSensitiveDiagnosticLabels() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AnalysisObservability observability = new AnalysisObservability(registry);
        String sensitive = "SECRET-FIXTURE-VALUE";
        String rawHcl = "resource \"aws_db_instance\" \"main\" { password = \"" + sensitive + "\" }";

        for (TerraformValidationFailureException.Category category
                : TerraformValidationFailureException.Category.values()) {
            TerraformValidationFailureException failure = new TerraformValidationFailureException(
                    category, "bounded message; raw=" + rawHcl);
            String expected = "terraform_" + category.name().toLowerCase(java.util.Locale.ROOT);

            assertThat(observability.category(failure)).isEqualTo(expected);
            observability.jobFailed(failure);
            assertThat(failureCount(registry, expected)).isEqualTo(1);
        }

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("category=\"terraform_init_timeout\"")
                .contains("category=\"terraform_provider_closure\"")
                .contains("category=\"terraform_init_configuration\"")
                .contains("category=\"terraform_validate_timeout\"")
                .contains("category=\"terraform_validate_configuration\"")
                .contains("category=\"terraform_internal\"")
                .doesNotContain(sensitive, rawHcl, "aws_db_instance", "password");
    }

    private AnalysisProviderFailureException providerFailure(AnalysisProviderFailureReason reason) {
        return new AnalysisProviderFailureException(reason, new RuntimeException());
    }

    private double failureCount(PrometheusMeterRegistry registry, String category) {
        return registry.find("terraformers.analysis.failures").tag("category", category).counter().count();
    }

    @Test
    void publishesFixedPrometheusMeterIdentitiesWithoutSensitiveLabels() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AnalysisObservability observability = new AnalysisObservability(registry);

        observability.jobStarted();
        observability.jobSucceeded();
        observability.jobFailed(new IllegalStateException("secret raw message"));
        observability.recordBedrock(() -> "ok");
        try {
            observability.recordBedrock(() -> {
                throw new IllegalStateException("secret raw message");
            });
        } catch (IllegalStateException ignored) {
        }
        observability.recordAoss(() -> "ok");
        try {
            observability.recordAoss(() -> {
                throw new IllegalStateException("secret raw message");
            });
        } catch (IllegalStateException ignored) {
        }
        observability.retrievedHits(3);
        observability.claimOutcome("initial_claim");
        observability.claimOutcome("not_claimed");
        observability.recordQueueWait(Duration.ofMillis(25));

        String scrape = registry.scrape();
        assertThat(scrape).contains(
                "terraformers_analysis_jobs",
                "terraformers_analysis_claims",
                "terraformers_analysis_queue_wait",
                "terraformers_bedrock_invocations",
                "terraformers_aoss_retrievals",
                "terraformers_aoss_retrieved_hits"
        );
        assertThat(scrape).contains("category=\"other\"").doesNotContain("secret raw message");
        assertThat(registry.find("terraformers.analysis.jobs").meters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .extracting(Tag::getKey)
                        .containsExactly("outcome")
        );
        assertThat(registry.find("terraformers.analysis.claims").meters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .extracting(Tag::getKey)
                        .containsExactly("outcome")
        );
        assertThat(registry.find("terraformers.analysis.queue.wait").timer().count()).isEqualTo(1);
    }
}
