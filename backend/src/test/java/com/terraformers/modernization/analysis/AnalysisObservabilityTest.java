package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
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

        String scrape = registry.scrape();
        assertThat(scrape).contains(
                "terraformers_analysis_jobs",
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
    }
}
