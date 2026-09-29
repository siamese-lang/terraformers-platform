package com.terraformers.modernization.analysis;

import java.time.Instant;
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

    private final AnalysisJobRepository repository;
    private final AnalysisJobOrchestrator orchestrator;
    private final AnalysisObservability observability;

    public AnalysisJobStateService(AnalysisJobRepository repository, AnalysisJobOrchestrator orchestrator,
            AnalysisObservability observability) {
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.observability = observability;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AnalysisJobEntity> claimEligible(String jobId, Instant now, Instant leaseExpiresAt) {
        int claimed = repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                now, leaseExpiresAt);
        return claimed == 0 ? Optional.empty() : repository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public List<String> findEligibleJobIds(Instant now, int batchSize) {
        return repository.findEligibleJobIds(AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, now,
                PageRequest.of(0, batchSize));
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
        return repository.renewLease(jobId, AnalysisJobStatus.RUNNING, generation, now, newLeaseExpiry) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean scheduleRetryOwned(String jobId, long generation, Instant now, Instant nextAttemptAt) {
        return repository.scheduleRetryOwned(jobId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.PENDING,
                generation, now, nextAttemptAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailedOwned(String jobId, long generation, Instant now, String failureReason) {
        boolean transitioned = repository.markFailedOwned(jobId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED,
                generation, now, failureReason) == 1;
        if (transitioned) {
            repository.findById(jobId).ifPresent(orchestrator::publishFailedProgress);
        }
        return transitioned;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordResultObjectIntentOwned(String jobId, long generation, Instant now,
            String bucket, String key) {
        return repository.recordResultObjectIntentOwned(jobId, AnalysisJobStatus.RUNNING, generation, now,
                bucket, key, AnalysisResultCleanupStatus.PENDING) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markResultCleanupCompleted(String jobId, long generation, String bucket, String key, Instant now) {
        return repository.markResultCleanupCompleted(jobId, generation, bucket, key, AnalysisResultCleanupStatus.PENDING,
                AnalysisResultCleanupStatus.COMPLETED, now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSucceededOwned(String jobId, long generation, Instant now, AnalysisResult result,
            ObjectReference reference) {
        Optional<AnalysisJobEntity> owned = repository.lockOwned(
                jobId, AnalysisJobStatus.RUNNING, generation, now);
        if (owned.isEmpty()) {
            return false;
        }
        AnalysisJobEntity entity = owned.get();
        if (!reference.bucket().equals(entity.getResultObjectIntentBucket())
                || !reference.key().equals(entity.getResultObjectIntentKey())
                || entity.getResultCleanupStatus() != AnalysisResultCleanupStatus.PENDING) {
            throw new IllegalStateException("owned result finalization does not match durable object intent");
        }
        boolean cleanupCompleted = false;
        try {
            ObjectWriteResult writeResult = orchestrator.storeTerraformDraft(reference, result);
            orchestrator.markSucceeded(
                    entity,
                    result,
                    writeResult,
                    orchestrator.registerGeneratedTerraform(entity.getProjectId(), result, writeResult)
            );
            if (!reference.key().equals(entity.getResultObjectKey())) {
                throw new IllegalStateException("successful result key does not match durable object intent");
            }
            entity.setResultCleanupStatus(AnalysisResultCleanupStatus.NOT_REQUIRED);
            entity.clearLease();
            repository.save(entity);
            repository.flush();
            return true;
        } catch (RuntimeException exception) {
            try {
                observability.recordStage(AnalysisTelemetryStage.COMPENSATION, () -> {
                    orchestrator.removeStoredDraft(reference);
                    return null;
                });
                cleanupCompleted = true;
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new AnalysisResultFinalizationException(exception, reference, cleanupCompleted);
        }
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
