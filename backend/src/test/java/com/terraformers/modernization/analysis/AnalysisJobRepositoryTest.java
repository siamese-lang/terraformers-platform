package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AnalysisJobRepositoryTest {

    @Autowired
    private AnalysisJobRepository repository;

    @Test
    void pendingJobCanBeClaimedExactlyOnce() {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(102L);
        entity.setSourceFileId(202L);
        entity.setSourceBucket("claim-bucket");
        entity.setSourceKey("uploads/claim.png");
        entity.setCorrelationId("claim-once");
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        entity.setStatus(AnalysisJobStatus.PENDING);
        String jobId = repository.saveAndFlush(entity).getId();

        int first = repository.claimPending(
                jobId,
                AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING,
                Instant.now()
        );
        int second = repository.claimPending(
                jobId,
                AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING,
                Instant.now()
        );

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(repository.findById(jobId))
                .get()
                .extracting(AnalysisJobEntity::getStatus)
                .isEqualTo(AnalysisJobStatus.RUNNING);
    }

    @Test
    void savesAnalysisJobLifecycleStateWithNumericProjectAndFileIds() {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(101L);
        entity.setSourceFileId(201L);
        entity.setSourceBucket("example-bucket");
        entity.setSourceKey("uploads/diagram.png");
        entity.setCorrelationId("corr-1");
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        entity.setStatus(AnalysisJobStatus.PENDING);

        AnalysisJobEntity saved = repository.saveAndFlush(entity);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getProjectId()).isEqualTo(101L);
        assertThat(saved.getSourceFileId()).isEqualTo(201L);
        assertThat(saved.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        assertThat(saved.getAnalysisMode()).isEqualTo(AnalysisMode.INTEGRATED_JAVA);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
