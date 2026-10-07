package com.terraformers.modernization.analysis;

import java.time.Instant;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import com.terraformers.modernization.storage.ObjectReference;
import com.terraformers.modernization.storage.ObjectWriteResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisJobStateService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobStateService.class);
    private final AnalysisRuntimeProperties properties;
    private final Clock clock;
    private final AnalysisJobRepository repository;
    private final AnalysisJobOrchestrator orchestrator;
    private final AnalysisObservability observability;

    public AnalysisJobStateService(AnalysisJobRepository repository, AnalysisJobOrchestrator orchestrator,
            AnalysisObservability observability, ObjectProvider<AnalysisRuntimeProperties> properties,
            ObjectProvider<Clock> clock) {
        this.properties = properties.getIfAvailable(AnalysisRuntimeProperties::new);
        this.clock = clock.getIfAvailable(Clock::systemUTC);
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.observability = observability;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AnalysisJobEntity> claimEligible(String jobId, Instant now, Instant leaseExpiresAt) {
        int claimed = repository.claimEligibleBeforeCutoff(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                now, leaseExpiresAt, now.minus(properties.getAcceptedAgeCutoff()));
        return claimed == 0 ? Optional.empty() : repository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public List<String> findEligibleJobIds(Instant now, int batchSize) {
        return repository.findEligibleJobIdsBeforeCutoff(AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, now,
                now.minus(properties.getAcceptedAgeCutoff()), PageRequest.of(0, batchSize));
    }

    @Transactional(readOnly = true)
    public List<String> findPendingCleanupJobIds(int batchSize) {
        return repository.findPendingCleanupJobIds(AnalysisJobStatus.FAILED,
                AnalysisResultCleanupStatus.PENDING, PageRequest.of(0, batchSize));
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisJobEntity> findForDispatchEvidence(String jobId) {
        return repository.findById(jobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewLease(String jobId, long generation, Instant now, Instant newLeaseExpiry) {
        return repository.renewLeaseBeforeCutoff(jobId, AnalysisJobStatus.RUNNING, generation, now, newLeaseExpiry,
                now.minus(properties.getAcceptedAgeCutoff())) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean scheduleRetryOwned(String jobId, long generation, Instant now, Instant nextAttemptAt) {
        return repository.scheduleRetryOwned(jobId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.PENDING,
                generation, now, nextAttemptAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailedOwned(String jobId, long generation, Instant now, String failureReason,
            EvidenceQualityAssessment quality) {
        String reasons = quality.reasons().stream().map(Enum::name).sorted()
                .reduce((left, right) -> left + "," + right).orElse("");
        boolean transitioned = repository.markFailedOwned(jobId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED,
                generation, now, failureReason, quality.contractVersion(), quality.technicalStatus(),
                quality.knowledgeStatus(), quality.qualityStatus(), quality.projectDecisionStatus(),
                quality.runtimeQualityBoundary(), reasons) == 1;
        if (transitioned) {
            repository.findById(jobId).ifPresent(entity -> afterCommit(() -> orchestrator.publishFailedProgress(entity)));
        }
        return transitioned;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailedOwned(String jobId, long generation, Instant now, String failureReason) {
        return markFailedOwned(jobId, generation, now, failureReason,
                TerminalQualityAssessmentMapper.failure(new IllegalStateException()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordResultObjectIntentOwned(String jobId, long generation, Instant now,
            String bucket, String key) {
        return repository.recordResultObjectIntentBeforeCutoff(jobId, AnalysisJobStatus.RUNNING, generation, now,
                bucket, key, AnalysisResultCleanupStatus.PENDING, now.minus(properties.getAcceptedAgeCutoff())) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markResultCleanupCompleted(String jobId, long generation, String bucket, String key, Instant now) {
        return repository.markResultCleanupCompleted(jobId, generation, bucket, key, AnalysisResultCleanupStatus.PENDING,
                AnalysisResultCleanupStatus.COMPLETED, now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rearmResultCleanup(String jobId, long generation, ObjectReference reference, Instant now) {
        return repository.rearmResultCleanup(jobId, generation, reference.bucket(), reference.key(),
                AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED, AnalysisResultCleanupStatus.PENDING, now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SweepResult terminalizeIneligible(Instant now) {
        // No provider, storage or progress-publisher I/O in the scheduler's transaction.
        EvidenceQualityAssessment quality = TerminalQualityAssessmentMapper.failure(new AnalysisJobBudgetExceededException());
        int expired = terminalize(now, true, AnalysisJobRunner.DEADLINE_FAILURE_REASON, quality);
        int exhausted = terminalize(now, false, AnalysisJobRunner.ATTEMPT_FAILURE_REASON, quality);
        afterCommit(() -> {
            observability.terminalSweepFailures("accepted_age_cutoff", expired, quality);
            observability.terminalSweepFailures("attempts_exhausted", exhausted, quality);
        });
        return new SweepResult(expired, exhausted);
    }

    private int terminalize(Instant now, boolean expired, String reason, EvidenceQualityAssessment quality) {
        return repository.terminalizeIneligible(AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                AnalysisJobStatus.FAILED, now, now.minus(properties.getAcceptedAgeCutoff()), expired,
                reason, quality.contractVersion(), quality.technicalStatus(), quality.knowledgeStatus(),
                quality.qualityStatus(), quality.projectDecisionStatus(), quality.runtimeQualityBoundary(), "");
    }

    public record SweepResult(int expired, int exhausted) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSucceededOwned(String jobId, long generation, Instant now, AnalysisResult result,
            ObjectReference reference, ObjectWriteResult writeResult) {
        Optional<AnalysisJobEntity> owned = repository.lockOwned(
                jobId, AnalysisJobStatus.RUNNING, generation, now);
        if (owned.isEmpty()) return false;
        AnalysisJobEntity entity = owned.get();
        // Fresh time AFTER the lock: waiting for DB ownership must not freeze the deadline check.
        if (entity.getStatus() != AnalysisJobStatus.RUNNING || entity.getClaimGeneration() != generation
                || !withinBudgetAndLease(entity, latest(now))) return false;
        if (!reference.bucket().equals(entity.getResultObjectIntentBucket())
                || !reference.key().equals(entity.getResultObjectIntentKey())
                || entity.getResultCleanupStatus() != AnalysisResultCleanupStatus.PENDING
                || !reference.bucket().equals(writeResult.bucket()) || !reference.key().equals(writeResult.key())) {
            throw new IllegalStateException("owned result finalization does not match durable object intent");
        }
        try {
            var file = orchestrator.registerGeneratedTerraform(entity.getProjectId(), result, writeResult);
            Instant terminalTime = latest(now);
            if (!withinBudgetAndLease(entity, terminalTime)) throw new AnalysisJobBudgetExceededException();
            // Mutation only; externally visible success is scheduled after the durable commit.
            orchestrator.markSucceeded(entity, result, writeResult, file);
            entity.setTerminalAt(terminalTime);
            entity.setQualityAssessment(result.qualityAssessment());
            if (!reference.key().equals(entity.getResultObjectKey())) {
                throw new IllegalStateException("successful result key does not match durable object intent");
            }
            entity.setResultCleanupStatus(AnalysisResultCleanupStatus.NOT_REQUIRED);
            entity.clearLease();
            repository.saveAndFlush(entity);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    if (deadlineReached(entity, latest(now))) throw new AnalysisJobBudgetExceededException();
                }
            });
            afterCommit(() -> orchestrator.publishSucceededProgress(entity));
            return true;
        } catch (RuntimeException exception) {
            // External compensation is performed by the runner AFTER this transaction rolls back.
            throw new AnalysisResultFinalizationException(exception, reference, false);
        }
    }

    private Instant latest(Instant supplied) {
        Instant current = clock.instant();
        return current.isAfter(supplied) ? current : supplied;
    }

    private boolean deadlineReached(AnalysisJobEntity entity, Instant now) {
        return entity.getCreatedAt() == null || !now.isBefore(entity.getCreatedAt().plus(properties.getAcceptedAgeCutoff()));
    }

    private boolean withinBudgetAndLease(AnalysisJobEntity entity, Instant now) {
        return !deadlineReached(entity, now) && entity.getLeaseExpiresAt() != null && entity.getLeaseExpiresAt().isAfter(now);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { action.run(); }
                catch (RuntimeException exception) {
                    log.warn("Committed analysis state retained after progress/telemetry failure errorClass={}",
                            exception.getClass().getSimpleName());
                }
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverPendingCleanup(String jobId) {
        Optional<AnalysisJobEntity> pending = repository.lockPendingCleanup(jobId, AnalysisJobStatus.FAILED,
                AnalysisResultCleanupStatus.PENDING);
        if (pending.isEmpty()) return false;
        AnalysisJobEntity entity = pending.get();
        ObjectReference reference = new ObjectReference(entity.getResultObjectIntentBucket(),
                entity.getResultObjectIntentKey());
        try {
            orchestrator.removeStoredDraft(reference);
        } catch (RuntimeException exception) {
            throw new CleanupRecoveryException(reference, exception);
        }
        entity.setResultCleanupStatus(AnalysisResultCleanupStatus.COMPLETED);
        repository.save(entity);
        repository.flush();
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deferPendingCleanup(String jobId, ObjectReference reference, Instant now) {
        return repository.touchPendingCleanup(jobId, AnalysisJobStatus.FAILED,
                AnalysisResultCleanupStatus.PENDING, reference.bucket(), reference.key(), now) == 1;
    }

    static final class CleanupRecoveryException extends RuntimeException {
        private final ObjectReference reference;

        CleanupRecoveryException(ObjectReference reference, RuntimeException cause) {
            super("analysis result cleanup recovery failed", cause);
            this.reference = reference;
        }

        ObjectReference reference() {
            return reference;
        }
    }
}
