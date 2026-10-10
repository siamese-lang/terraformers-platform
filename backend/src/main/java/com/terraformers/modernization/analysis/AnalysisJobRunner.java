package com.terraformers.modernization.analysis;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.terraformers.modernization.storage.ObjectReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalysisJobRunner {
    static final String DEADLINE_FAILURE_REASON = "분석 작업의 대기 및 처리 시간 한도를 초과했습니다. 필요하면 새 분석을 시작해 주세요.";
    static final String ATTEMPT_FAILURE_REASON = "분석 작업의 실행 소유권이 만료되어 완료하지 못했습니다. 필요하면 새 분석을 시작해 주세요.";
    static final String TIMEOUT_FAILURE_REASON = "AI 모델의 응답 시간이 초과되었습니다. 잠시 후 새 분석을 시작해 주세요.";
    static final String TRUNCATED_FAILURE_REASON = "아키텍처가 복잡해 AI 출력 한도를 초과했습니다. 핵심 구성만 남기거나 이미지를 여러 장으로 나누어 다시 시도해 주세요.";
    static final String FORMAT_FAILURE_REASON = "AI 응답 형식을 확인하지 못했습니다. 잠시 후 새 분석을 시작해 주세요.";
    static final String REJECTED_INPUT_FAILURE_REASON = "아키텍처 구성요소와 연결 관계를 확인할 수 없습니다. 시스템 구성도, 네트워크 구조도 또는 서비스 간 흐름이 표시된 이미지를 업로드해 주세요.";
    static final String GENERIC_FAILURE_REASON = "분석을 완료하지 못했습니다. 잠시 후 새 분석을 시작해 주세요.";
    private static final Logger log = LoggerFactory.getLogger(AnalysisJobRunner.class);

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

    private AnalysisDiagnosticStorage diagnostics;

    @Autowired
    void setDiagnosticStorage(AnalysisDiagnosticStorage diagnostics) { this.diagnostics = diagnostics; }

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
            log.info("Analysis job execution started");
            AnalysisDiagnosticEvidence evidence = AnalysisDiagnosticEvidence.open(runningJob);
            if (diagnostics != null) diagnostics.begin(runningJob);
            AnalysisResult result = null;
            ObjectReference writtenReference = null;
            boolean writeAttempted = false;
            boolean successCommitted = false;
            try {
                result = observability.recordStage(AnalysisTelemetryStage.ANALYSIS_EXECUTION,
                        () -> orchestrator.executeProviderAndValidate(runningJob));
                if (leaseLost.get()) {
                    evidence.fenced();
                    log.warn("Analysis result not finalized because durable ownership was lost generation={}", generation);
                    return;
                }
                ObjectReference reference = orchestrator.resolveResultObjectReference(runningJob);
                if (!stateService.recordResultObjectIntentOwned(jobId, generation, clock.instant(),
                        reference.bucket(), reference.key())) {
                    leaseLost.set(true);
                    evidence.fenced();
                    log.warn("Analysis result intent rejected because durable ownership was lost generation={}", generation);
                    return;
                }
                if (leaseLost.get()) {
                    evidence.fenced();
                    log.warn("Analysis result not written because durable ownership was lost generation={}", generation);
                    return;
                }
                AnalysisDiagnosticEvidence.stage("result_finalization");
                AnalysisResult completed = result;
                writtenReference = reference;
                writeAttempted = true;
                boolean finalized = observability.recordStage(AnalysisTelemetryStage.RESULT_FINALIZE, () -> {
                    // No analysis-job row lock is held during external write.
                    var writeResult = orchestrator.storeTerraformDraft(reference, completed);
                    return stateService.markSucceededOwned(jobId, generation, clock.instant(), completed,
                            reference, writeResult);
                });
                successCommitted = finalized;
                if (finalized) {
                    evidence.captured("result_finalization");
                    observability.jobSucceeded();
                }
                if (finalized) observability.terminalQuality(completed.qualityAssessment());
                else {
                    leaseLost.set(true);
                    evidence.fenced();
                    log.warn("Analysis success finalization rejected because durable ownership was lost generation={}", generation);
                }
            } catch (RuntimeException exception) {
                evidence.failed(exception);
                if (leaseLost.get()) {
                    log.warn("Analysis failure not finalized because durable ownership was lost generation={}", generation);
                    return;
                }
                if (exception instanceof AnalysisResultFinalizationException finalizationException) {
                    if (finalizationException.cleanupCompleted()) {
                        boolean recorded = stateService.markResultCleanupCompleted(jobId, generation,
                                finalizationException.reference().bucket(), finalizationException.reference().key(),
                                clock.instant());
                        observability.cleanupOutcome(recorded ? "immediate_completed" : "immediate_pending");
                    } else {
                        observability.cleanupOutcome("immediate_pending");
                    }
                }
                if (result == null && isRetryable(exception)) {
                    observability.retryOutcome("exhausted");
                    log.warn("Analysis retry budget exhausted attempt={} generation={}",
                            runningJob.getAttemptCount(), generation);
                }
                EvidenceQualityAssessment quality = TerminalQualityAssessmentMapper.failure(exception);
                boolean failed = stateService.markFailedOwned(jobId, generation, clock.instant(),
                        safeFailureReason(exception), quality);
                if (failed) {
                    observability.jobFailed(exception);
                    observability.terminalQuality(quality);
                    log.error("Analysis job failed outcome=failed exceptionCategory={} generation={}",
                            observability.category(exception), generation);
                } else {
                    leaseLost.set(true);
                    log.warn("Analysis failure finalization rejected because durable ownership was lost generation={}", generation);
                }
            } finally {
                // Preserve private evidence even for failed validation or rejected late finalization.
                heartbeat.cancel(false);
                try { if (diagnostics != null) diagnostics.finish(runningJob, evidence); }
                finally { evidence.close(); }
                if (writeAttempted && !successCommitted) cleanupWrittenResult(jobId, generation, writtenReference);
                observability.stopAnalysis(sample);
            }
        }
    }

    private void cleanupWrittenResult(String jobId, long generation, ObjectReference reference) {
        try {
            // A failed-job cleanup can finish before a blocked writer returns. Re-arm AFTER write,
            // serialized by the existing cleanup lock, so the later object cannot disappear from accountability.
            if (!stateService.rearmResultCleanup(jobId, generation, reference, clock.instant())) return;
            observability.recordStage(AnalysisTelemetryStage.COMPENSATION, () -> {
                orchestrator.removeStoredDraft(reference);
                return null;
            });
            boolean completed = stateService.markResultCleanupCompleted(jobId, generation,
                    reference.bucket(), reference.key(), clock.instant());
            observability.cleanupOutcome(completed ? "immediate_completed" : "immediate_pending");
        } catch (RuntimeException exception) {
            observability.cleanupOutcome("immediate_pending");
            log.warn("Written analysis result retains cleanup accountability errorClass={}",
                    exception.getClass().getSimpleName());
        }
    }

    private boolean isRetryable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof AnalysisProviderTimeoutException) return true;
            current = current.getCause();
        }
        return false;
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
                    case CONTENT_BLOCKED -> "Analysis provider blocked the response";
                    case EMPTY_RESPONSE -> "Analysis provider returned no usable response";
                    case RATE_LIMITED, PROVIDER_ERROR -> GENERIC_FAILURE_REASON;
                    case INPUT_REJECTED -> REJECTED_INPUT_FAILURE_REASON;
                    case RESPONSE_FORMAT -> FORMAT_FAILURE_REASON;
                };
            }
            if (current instanceof AnalysisJobBudgetExceededException) return DEADLINE_FAILURE_REASON;
            if (current instanceof SocketTimeoutException || current instanceof AnalysisProviderTimeoutException) return TIMEOUT_FAILURE_REASON;
            current = current.getCause();
        }
        return GENERIC_FAILURE_REASON;
    }
}
