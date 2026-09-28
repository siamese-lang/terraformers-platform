package com.terraformers.modernization.analysis;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalysisJobRunner {
    static final String TIMEOUT_FAILURE_REASON = "AI 모델의 응답 시간이 초과되었습니다. 잠시 후 새 분석을 시작해 주세요.";
    static final String TRUNCATED_FAILURE_REASON = "아키텍처가 복잡해 AI 출력 한도를 초과했습니다. 핵심 구성만 남기거나 이미지를 여러 장으로 나누어 다시 시도해 주세요.";
    static final String FORMAT_FAILURE_REASON = "AI 응답 형식을 확인하지 못했습니다. 잠시 후 새 분석을 시작해 주세요.";
    static final String REJECTED_INPUT_FAILURE_REASON = "아키텍처 구성요소와 연결 관계를 확인할 수 없습니다. 시스템 구성도, 네트워크 구조도 또는 서비스 간 흐름이 표시된 이미지를 업로드해 주세요.";
    static final String GENERIC_FAILURE_REASON = "분석을 완료하지 못했습니다. 잠시 후 새 분석을 시작해 주세요.";
    private static final Logger log = LoggerFactory.getLogger(AnalysisJobRunner.class);
    private static final ScheduledExecutorService COMPATIBILITY_TEST_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "analysis-test-lease-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private final AnalysisJobOrchestrator orchestrator;
    private final AnalysisJobStateService stateService;
    private final AnalysisObservability observability;
    private final AnalysisRuntimeProperties properties;
    private final ScheduledExecutorService leaseScheduler;
    private final Clock clock;

    @Autowired
    public AnalysisJobRunner(AnalysisJobOrchestrator orchestrator, AnalysisJobStateService stateService,
            AnalysisObservability observability, AnalysisRuntimeProperties properties,
            @Qualifier("analysisLeaseScheduler") ScheduledExecutorService leaseScheduler, Clock clock) {
        this.orchestrator = orchestrator;
        this.stateService = stateService;
        this.observability = observability;
        this.properties = properties;
        this.leaseScheduler = leaseScheduler;
        this.clock = clock;
    }

    AnalysisJobRunner(AnalysisJobOrchestrator orchestrator, AnalysisJobStateService stateService,
            AnalysisObservability observability) {
        this(orchestrator, stateService, observability, new AnalysisRuntimeProperties(),
                COMPATIBILITY_TEST_SCHEDULER, Clock.systemUTC());
    }

    public void run(String jobId) {
        try (AnalysisLogCorrelation ignored = AnalysisLogCorrelation.forJob(jobId)) {
            Instant claimTime = clock.instant();
            Instant previousLeaseExpiry = stateService.findForDispatchEvidence(jobId)
                    .map(AnalysisJobEntity::getLeaseExpiresAt).orElse(null);
            AnalysisJobEntity runningJob = stateService.claimEligible(
                    jobId, claimTime, claimTime.plus(properties.getLeaseDuration())).orElse(null);
            if (runningJob == null) {
                observability.claimOutcome("not_claimed");
                log.info("Analysis job skipped outcome=not_claimed reason=not_eligible");
                return;
            }
            long generation = runningJob.getClaimGeneration();
            boolean reclaim = runningJob.getAttemptCount() > 1;
            observability.claimOutcome(reclaim ? "reclaim" : "initial_claim");
            if (reclaim) {
                if (previousLeaseExpiry != null) {
                    observability.recordRecovery(Duration.between(previousLeaseExpiry, claimTime));
                }
            } else if (runningJob.getCreatedAt() != null) {
                observability.recordQueueWait(Duration.between(runningJob.getCreatedAt(), claimTime));
            }
            orchestrator.markRunning(runningJob);
            log.info("Analysis job claimed outcome={} generation={} attempt={}",
                    reclaim ? "reclaim" : "initial_claim", generation, runningJob.getAttemptCount());

            AtomicBoolean leaseLost = new AtomicBoolean();
            ScheduledFuture<?> heartbeat = startHeartbeat(jobId, generation, leaseLost);
            io.micrometer.core.instrument.Timer.Sample sample = observability.startAnalysis();
            observability.jobStarted();
            AnalysisJobExecution execution = null;
            try {
                execution = observability.recordStage(AnalysisTelemetryStage.ANALYSIS_EXECUTION,
                        () -> orchestrator.executeProviderAndStoreDraft(runningJob));
                if (leaseLost.get()) {
                    log.warn("Analysis result not finalized because durable ownership was lost generation={}", generation);
                    return;
                }
                AnalysisJobExecution completed = execution;
                boolean finalized = observability.recordStage(AnalysisTelemetryStage.RESULT_FINALIZE,
                        () -> stateService.markSucceededOwned(jobId, generation, clock.instant(), completed));
                if (finalized) observability.jobSucceeded();
                else {
                    leaseLost.set(true);
                    log.warn("Analysis success finalization rejected because durable ownership was lost generation={}", generation);
                }
            } catch (RuntimeException exception) {
                if (leaseLost.get()) {
                    log.warn("Analysis failure not finalized because durable ownership was lost generation={}", generation);
                    return;
                }
                if (execution != null && exception instanceof AnalysisResultFinalizationException) {
                    compensateStoredDraft(execution);
                }
                boolean failed = stateService.markFailedOwned(jobId, generation, clock.instant(), safeFailureReason(exception));
                if (failed) {
                    observability.jobFailed(exception);
                    log.error("Analysis job failed outcome=failed exceptionCategory={} generation={}",
                            observability.category(exception), generation);
                } else {
                    leaseLost.set(true);
                    log.warn("Analysis failure finalization rejected because durable ownership was lost generation={}", generation);
                }
            } finally {
                heartbeat.cancel(false);
                observability.stopAnalysis(sample);
            }
        }
    }

    private void compensateStoredDraft(AnalysisJobExecution execution) {
        try {
            observability.recordStage(AnalysisTelemetryStage.COMPENSATION, () -> {
                orchestrator.removeStoredDraft(execution.writeResult());
                return null;
            });
            log.warn("Compensated stored analysis draft after owned relational finalization failure");
        } catch (RuntimeException cleanupException) {
            log.error("Analysis draft compensation failed cleanupException={}",
                    cleanupException.getClass().getSimpleName());
        }
    }

    private ScheduledFuture<?> startHeartbeat(String jobId, long generation, AtomicBoolean leaseLost) {
        long intervalMillis = properties.getLeaseRenewInterval().toMillis();
        return leaseScheduler.scheduleAtFixedRate(() -> {
            if (leaseLost.get()) return;
            Instant now = clock.instant();
            boolean renewed;
            try {
                renewed = stateService.renewLease(jobId, generation, now, now.plus(properties.getLeaseDuration()));
            } catch (RuntimeException exception) {
                renewed = false;
                log.warn("Analysis lease renewal failed generation={} errorClass={}", generation,
                        exception.getClass().getSimpleName());
            }
            observability.leaseRenewal(renewed);
            if (!renewed) {
                leaseLost.set(true);
                log.warn("Analysis lease ownership lost generation={}", generation);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private String safeFailureReason(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AnalysisProviderFailureException providerFailure) {
                return switch (providerFailure.reason()) {
                    case OUTPUT_TRUNCATED -> TRUNCATED_FAILURE_REASON;
                    case INPUT_REJECTED -> REJECTED_INPUT_FAILURE_REASON;
                    case RESPONSE_FORMAT -> FORMAT_FAILURE_REASON;
                };
            }
            if (current instanceof SocketTimeoutException || current instanceof AnalysisProviderTimeoutException) return TIMEOUT_FAILURE_REASON;
            current = current.getCause();
        }
        return GENERIC_FAILURE_REASON;
    }
}
