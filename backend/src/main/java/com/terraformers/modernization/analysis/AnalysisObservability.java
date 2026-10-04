package com.terraformers.modernization.analysis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.terraformers.modernization.reference.ArchitectureFactsExtractionException;

@Component
public class AnalysisObservability {

    private static final Logger log = LoggerFactory.getLogger(AnalysisObservability.class);

    private final MeterRegistry meterRegistry;

    public AnalysisObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startAnalysis() {
        return Timer.start(meterRegistry);
    }

    public void stopAnalysis(Timer.Sample sample) {
        sample.stop(Timer.builder("terraformers.analysis.duration").register(meterRegistry));
    }

    public void jobStarted() {
        jobs("started").increment();
    }

    public void jobSucceeded() {
        jobs("succeeded").increment();
    }

    public void jobFailed(Throwable exception) {
        jobs("failed").increment();
        failures("terraformers.analysis.failures", category(exception)).increment();
    }

    public void terminalQuality(EvidenceQualityAssessment quality) {
        if (quality == null) return;
        Counter.builder("terraformers.analysis.quality.terminal")
                .tag("contract", quality.contractVersion())
                .tag("technical", quality.technicalStatus().name())
                .tag("knowledge", quality.knowledgeStatus().name())
                .tag("quality", quality.qualityStatus().name())
                .tag("project_decision", quality.projectDecisionStatus().name())
                .tag("runtime_boundary", quality.runtimeQualityBoundary().name())
                .register(meterRegistry).increment();
        quality.reasons().forEach(reason -> Counter.builder("terraformers.analysis.quality.reasons")
                .tag("reason", reason.name()).register(meterRegistry).increment());
        log.info("terminal quality contract={} technical={} knowledge={} quality={} projectDecision={} runtimeBoundary={} reasons={}",
                quality.contractVersion(), quality.technicalStatus(), quality.knowledgeStatus(),
                quality.qualityStatus(), quality.projectDecisionStatus(),
                quality.runtimeQualityBoundary(),
                quality.reasons().stream().map(Enum::name).toList());
    }

    public void claimOutcome(String outcome) {
        Counter.builder("terraformers.analysis.claims")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void dispatchOutcome(String outcome) {
        Counter.builder("terraformers.analysis.dispatch")
                .tag("outcome", outcome).register(meterRegistry).increment();
        if ("executor_rejected".equals(outcome)) {
            executorRejections().increment();
        }
    }

    public void leaseRenewal(boolean renewed) {
        Counter.builder("terraformers.analysis.lease.renewals")
                .tag("outcome", renewed ? "renewed" : "lost")
                .register(meterRegistry).increment();
    }

    public void retryOutcome(String outcome) {
        Counter.builder("terraformers.analysis.retries")
                .tag("outcome", outcome)
                .register(meterRegistry).increment();
    }

    public void cleanupOutcome(String outcome) {
        Counter.builder("terraformers.analysis.cleanup")
                .tag("outcome", outcome)
                .register(meterRegistry).increment();
    }

    public void cleanupScanCandidates(int count) {
        DistributionSummary.builder("terraformers.analysis.cleanup.scan.candidates")
                .register(meterRegistry).record(count);
    }

    public void dispatchScanCandidates(int count) {
        DistributionSummary.builder("terraformers.analysis.dispatch.scan.candidates")
                .register(meterRegistry).record(count);
    }

    public void recordRecovery(Duration delay) {
        Timer.builder("terraformers.analysis.recovery.delay").register(meterRegistry)
                .record(delay.isNegative() ? Duration.ZERO : delay);
    }

    public void recordQueueWait(Duration wait) {
        Timer.builder("terraformers.analysis.queue.wait")
                .register(meterRegistry)
                .record(wait.isNegative() ? Duration.ZERO : wait);
    }

    public <T> T recordStage(AnalysisTelemetryStage stage, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            T value = operation.get();
            log.info(
                    "analysis stage outcome=success stage={} elapsedMs={}",
                    stage.tag(),
                    elapsedMs(startedAt)
            );
            return value;
        } catch (RuntimeException exception) {
            outcome = "failure";
            String category = category(exception);
            Counter.builder("terraformers.analysis.stage.failures")
                    .tag("stage", stage.tag())
                    .tag("category", category)
                    .register(meterRegistry)
                    .increment();
            log.warn(
                    "analysis stage outcome=failure stage={} category={} errorClass={} elapsedMs={}",
                    stage.tag(),
                    category,
                    exception.getClass().getSimpleName(),
                    elapsedMs(startedAt)
            );
            throw exception;
        } finally {
            sample.stop(Timer.builder("terraformers.analysis.stage.duration")
                    .tag("stage", stage.tag())
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    public <T> T recordBedrock(Supplier<T> operation) {
        return recordExternal("terraformers.bedrock", operation);
    }

    public <T> T recordAoss(Supplier<T> operation) {
        return recordExternal("terraformers.aoss", operation);
    }

    public void retrievedHits(int count) {
        DistributionSummary.builder("terraformers.aoss.retrieved_hits").register(meterRegistry).record(count);
    }

    private Counter jobs(String outcome) {
        return Counter.builder("terraformers.analysis.jobs").tag("outcome", outcome).register(meterRegistry);
    }

    private Counter failures(String name, String category) {
        return Counter.builder(name).tag("category", category).register(meterRegistry);
    }

    private Counter executorRejections() {
        return Counter.builder("terraformers.analysis.executor.rejections").register(meterRegistry);
    }

    private <T> T recordExternal(String prefix, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T value = operation.get();
            Counter.builder(prefix + (prefix.endsWith("bedrock") ? ".invocations" : ".retrievals"))
                    .tag("outcome", "success").register(meterRegistry).increment();
            return value;
        } catch (RuntimeException exception) {
            Counter.builder(prefix + (prefix.endsWith("bedrock") ? ".invocations" : ".retrievals"))
                    .tag("outcome", "failure").register(meterRegistry).increment();
            failures(prefix + ".failures", category(exception)).increment();
            throw exception;
        } finally {
            sample.stop(Timer.builder(prefix + ".duration").register(meterRegistry));
        }
    }

    public String category(Throwable exception) {
        if (exception instanceof GeneratedTerraformContractViolation generatedFailure) {
            return switch (generatedFailure.reason()) {
                case MODULE_BLOCK -> "generated_terraform_contract_module";
                case RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT -> "generated_terraform_contract_provider";
            };
        }
        if (exception instanceof TerraformValidationFailureException terraformFailure) {
            return "terraform_" + terraformFailure.category().name().toLowerCase(Locale.ROOT);
        }
        if (exception instanceof AnalysisResultFinalizationException) return "result_finalization";
        if (exception instanceof AnalysisProviderFailureException providerFailure) {
            return switch (providerFailure.reason()) {
                case OUTPUT_TRUNCATED -> "truncated_output";
                case CONTENT_BLOCKED -> "provider_content_blocked";
                case EMPTY_RESPONSE -> "provider_empty_response";
                case RATE_LIMITED -> "provider_rate_limited";
                case PROVIDER_ERROR -> "provider_error";
                case INPUT_REJECTED -> "rejected_input";
                case RESPONSE_FORMAT -> "response_format";
            };
        }
        if (exception instanceof ArchitectureFactsExtractionException factsFailure) {
            return switch (factsFailure.reason()) {
                case PROVIDER_CONTENT_BLOCKED -> "provider_content_blocked";
                case PROVIDER_TIMEOUT -> "timeout";
                case PROVIDER_RATE_LIMITED -> "provider_rate_limited";
                case PROVIDER_ERROR, PROVIDER_RUNTIME -> "provider_error";
                case RESPONSE_TRUNCATED -> "truncated_output";
                case EMPTY_RESPONSE -> "provider_empty_response";
                case INVALID_RESPONSE, EMPTY_FACTS -> "response_format";
            };
        }
        if (exception instanceof AnalysisProviderTimeoutException) return "timeout";
        if (ProviderFailureClassifier.isRateLimited(exception)) return "provider_rate_limited";
        if (ProviderFailureClassifier.isTimeout(exception)) return "timeout";
        if (exception instanceof RejectedExecutionException) return "executor_rejected";
        String simple = exception == null ? "" : exception.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simple.contains("timeout")) return "timeout";
        if (simple.contains("validation")) return "validation";
        if (simple.contains("rejected")) return "rejected_input";
        if (simple.contains("format")) return "response_format";
        if (simple.contains("truncated")) return "truncated_output";
        return "other";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
