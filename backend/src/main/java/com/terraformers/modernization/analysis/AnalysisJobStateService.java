package com.terraformers.modernization.analysis;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisJobStateService {

    private final AnalysisJobRepository repository;
    private final AnalysisJobOrchestrator orchestrator;

    public AnalysisJobStateService(AnalysisJobRepository repository, AnalysisJobOrchestrator orchestrator) {
        this.repository = repository;
        this.orchestrator = orchestrator;
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
    public boolean markResultCleanupCompleted(String jobId, String bucket, String key, Instant now) {
        return repository.markResultCleanupCompleted(jobId, bucket, key, AnalysisResultCleanupStatus.PENDING,
                AnalysisResultCleanupStatus.COMPLETED, now) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSucceededOwned(String jobId, long generation, Instant now, AnalysisJobExecution execution) {
        try {
            Optional<AnalysisJobEntity> owned = repository.lockOwned(
                    jobId, AnalysisJobStatus.RUNNING, generation, now);
            if (owned.isEmpty()) {
                return false;
            }
            AnalysisJobEntity entity = owned.get();
            orchestrator.markSucceeded(
                    entity,
                    execution.result(),
                    execution.writeResult(),
                    orchestrator.registerGeneratedTerraform(entity.getProjectId(), execution)
            );
            entity.clearLease();
            repository.save(entity);
            repository.flush();
            return true;
        } catch (RuntimeException exception) {
            throw new AnalysisResultFinalizationException(exception);
        }
    }
}
