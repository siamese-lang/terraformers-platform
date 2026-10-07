package com.terraformers.modernization.analysis;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Bridges durable MariaDB eligibility to the bounded local worker pool. The local set only avoids
 * redundant submissions; claim generation and leases remain the ownership boundary.
 */
@Component
public class AnalysisJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobDispatcher.class);

    private final AnalysisJobStateService stateService;
    private final AnalysisJobRunner runner;
    private final Executor executor;
    private final AnalysisRuntimeProperties properties;
    private final AnalysisObservability observability;
    private final Clock clock;
    private final Set<String> locallySubmitted = ConcurrentHashMap.newKeySet();

    public AnalysisJobDispatcher(
            AnalysisJobStateService stateService,
            AnalysisJobRunner runner,
            @Qualifier("analysisJobExecutor") Executor executor,
            AnalysisRuntimeProperties properties,
            AnalysisObservability observability,
            Clock clock
    ) {
        this.stateService = stateService;
        this.runner = runner;
        this.executor = executor;
        this.properties = properties;
        this.observability = observability;
        this.clock = clock;
    }

    public void dispatchEligible() {
        Instant now = clock.instant();
        // Accepted-age safety remains active even if normal discovery is disabled.
        stateService.terminalizeIneligible(now);
        if (!properties.isDispatchEnabled()) return;
        var candidates = stateService.findEligibleJobIds(now, properties.getDispatchBatchSize());
        observability.dispatchScanCandidates(candidates.size());
        candidates.forEach(this::submit);
    }

    public boolean submit(String jobId) {
        if (!locallySubmitted.add(jobId)) {
            observability.dispatchOutcome("duplicate_suppressed");
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    runner.run(jobId);
                } finally {
                    locallySubmitted.remove(jobId);
                }
            });
            observability.dispatchOutcome("submitted");
            return true;
        } catch (RejectedExecutionException exception) {
            locallySubmitted.remove(jobId);
            observability.dispatchOutcome("executor_rejected");
            log.warn("Analysis job dispatch rejected; durable eligibility preserved analysisJobId={}", jobId);
            return false;
        }
    }

    boolean isLocallySubmitted(String jobId) {
        return locallySubmitted.contains(jobId);
    }
}
