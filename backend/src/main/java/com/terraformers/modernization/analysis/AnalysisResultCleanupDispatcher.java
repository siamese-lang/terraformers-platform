package com.terraformers.modernization.analysis;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Submits bounded, idempotent cleanup recovery while MariaDB remains the durable work source. */
@Component
public class AnalysisResultCleanupDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AnalysisResultCleanupDispatcher.class);

    private final AnalysisJobStateService stateService;
    private final Executor executor;
    private final AnalysisRuntimeProperties properties;
    private final AnalysisObservability observability;
    private final Set<String> locallySubmitted = ConcurrentHashMap.newKeySet();

    public AnalysisResultCleanupDispatcher(AnalysisJobStateService stateService,
            @Qualifier("analysisJobExecutor") Executor executor, AnalysisRuntimeProperties properties,
            AnalysisObservability observability) {
        this.stateService = stateService;
        this.executor = executor;
        this.properties = properties;
        this.observability = observability;
    }

    public void dispatchPending() {
        if (!properties.isDispatchEnabled()) return;
        var candidates = stateService.findPendingCleanupJobIds(properties.getDispatchBatchSize());
        observability.cleanupScanCandidates(candidates.size());
        candidates.forEach(this::submit);
    }

    public boolean submit(String jobId) {
        if (!locallySubmitted.add(jobId)) {
            observability.cleanupOutcome("duplicate_suppressed");
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    boolean completed = observability.recordStage(AnalysisTelemetryStage.CLEANUP_RECOVERY,
                            () -> stateService.recoverPendingCleanup(jobId));
                    observability.cleanupOutcome(completed ? "recovery_completed" : "recovery_failed");
                } catch (RuntimeException exception) {
                    observability.cleanupOutcome("recovery_failed");
                    log.warn("Analysis result cleanup recovery failed errorClass={}",
                            exception.getClass().getSimpleName());
                } finally {
                    locallySubmitted.remove(jobId);
                }
            });
            observability.cleanupOutcome("submitted");
            return true;
        } catch (RejectedExecutionException exception) {
            locallySubmitted.remove(jobId);
            observability.cleanupOutcome("executor_rejected");
            return false;
        }
    }

    boolean isLocallySubmitted(String jobId) {
        return locallySubmitted.contains(jobId);
    }
}
