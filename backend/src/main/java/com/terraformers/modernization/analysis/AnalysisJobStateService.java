package com.terraformers.modernization.analysis;

import java.time.Instant;
import java.util.NoSuchElementException;
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
    public int reconcileInterrupted(String failureReason) {
        return repository.failInterrupted(
                AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING,
                AnalysisJobStatus.FAILED,
                failureReason,
                Instant.now()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AnalysisJobEntity> claimPending(String jobId) {
        int claimed = repository.claimPending(
                jobId,
                AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING,
                Instant.now()
        );
        if (claimed == 0) {
            requireJob(jobId);
            return Optional.empty();
        }

        AnalysisJobEntity entity = requireJob(jobId);
        orchestrator.markRunning(entity);
        return Optional.of(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(String jobId, AnalysisJobExecution execution) {
        try {
            AnalysisJobEntity entity = requireJob(jobId);
            orchestrator.markSucceeded(
                    entity,
                    execution.result(),
                    execution.writeResult(),
                    orchestrator.registerGeneratedTerraform(entity.getProjectId(), execution)
            );
            repository.save(entity);
            repository.flush();
        } catch (RuntimeException exception) {
            throw new AnalysisResultFinalizationException(exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String jobId, String failureReason) {
        AnalysisJobEntity entity = requireJob(jobId);
        orchestrator.markFailed(entity, failureReason);
        repository.save(entity);
    }

    private AnalysisJobEntity requireJob(String jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("analysis job not found: " + jobId));
    }
}
