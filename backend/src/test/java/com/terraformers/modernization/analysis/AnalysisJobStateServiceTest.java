package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(AnalysisJobStateService.class)
class AnalysisJobStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired AnalysisJobRepository repository;
    @Autowired AnalysisJobStateService stateService;
    @MockBean AnalysisJobOrchestrator orchestrator;

    @Test
    void staleGenerationCannotCreateRelationalMetadataOrSucceedAfterReclaim() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(1L); job.setSourceFileId(2L); job.setSourceBucket("bucket");
        job.setSourceKey("source.png"); job.setStatus(AnalysisJobStatus.PENDING);
        String id = repository.saveAndFlush(job).getId();
        repository.claimEligible(id, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, NOW.plusSeconds(10));
        repository.claimEligible(id, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW.plusSeconds(10), NOW.plusSeconds(70));

        boolean finalized = stateService.markSucceededOwned(id, 1, NOW.plusSeconds(11),
                org.mockito.Mockito.mock(AnalysisJobExecution.class));

        assertThat(finalized).isFalse();
        verify(orchestrator, never()).registerGeneratedTerraform(any(), any());
        AnalysisJobEntity current = repository.findById(id).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(current.getClaimGeneration()).isEqualTo(2);
        assertThat(current.getResultFileId()).isNull();
    }

    @Test
    void heartbeatMovesLeaseForwardWithoutChangingAttemptOrGeneration() {
        AnalysisJobEntity job = pendingJob();
        String id = repository.saveAndFlush(job).getId();
        Instant firstLease = NOW.plusSeconds(60);
        Instant renewedLease = NOW.plusSeconds(90);
        repository.claimEligible(id, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, NOW, firstLease);

        assertThat(stateService.renewLease(id, 1, NOW.plusSeconds(20), renewedLease)).isTrue();

        AnalysisJobEntity renewed = repository.findById(id).orElseThrow();
        assertThat(renewed.getLeaseExpiresAt()).isEqualTo(renewedLease);
        assertThat(renewed.getAttemptCount()).isEqualTo(1);
        assertThat(renewed.getClaimGeneration()).isEqualTo(1);
    }

    @Test
    void onlyOwnedFailurePublishesFailedProgress() {
        AnalysisJobEntity job = pendingJob();
        String id = repository.saveAndFlush(job).getId();
        repository.claimEligible(id, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, NOW.plusSeconds(60));

        assertThat(stateService.markFailedOwned(id, 99, NOW.plusSeconds(1), "stale")).isFalse();
        verify(orchestrator, never()).publishFailedProgress(any());

        assertThat(stateService.markFailedOwned(id, 1, NOW.plusSeconds(1), "owned")).isTrue();
        verify(orchestrator, times(1)).publishFailedProgress(any());
        AnalysisJobEntity failed = repository.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.getNextAttemptAt()).isNull();
    }

    private AnalysisJobEntity pendingJob() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(1L);
        job.setSourceFileId(2L);
        job.setSourceBucket("bucket");
        job.setSourceKey("source.png");
        job.setStatus(AnalysisJobStatus.PENDING);
        return job;
    }
}
