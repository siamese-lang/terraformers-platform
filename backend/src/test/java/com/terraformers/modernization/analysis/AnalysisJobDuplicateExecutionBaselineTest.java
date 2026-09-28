package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.storage.ObjectWriteResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@Import(AnalysisJobStateService.class)
class AnalysisJobDuplicateExecutionBaselineTest {

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private AnalysisJobStateService stateService;

    @MockBean
    private AnalysisJobOrchestrator orchestrator;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicateDeliveryReexecutesSucceededJobWithoutStateGuard() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(701L);
        job.setSourceFileId(801L);
        job.setSourceBucket("duplicate-baseline-bucket");
        job.setSourceKey("source/duplicate-baseline.png");
        job.setCorrelationId("duplicate-delivery");
        job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        job.setStatus(AnalysisJobStatus.PENDING);
        String jobId = repository.saveAndFlush(job).getId();

        List<AnalysisJobStatus> statusBeforeMarkRunning = new ArrayList<>();
        doAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            statusBeforeMarkRunning.add(entity.getStatus());
            entity.setStatus(AnalysisJobStatus.RUNNING);
            return null;
        }).when(orchestrator).markRunning(any(AnalysisJobEntity.class));

        AnalysisJobExecution execution = new AnalysisJobExecution(
                new AnalysisResult(
                        "duplicate-baseline",
                        "resource \"aws_s3_bucket\" \"duplicate\" {}",
                        "duplicate baseline",
                        List.of("S3"),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new ObjectWriteResult(
                        "metadata-only",
                        false,
                        "duplicate-baseline-bucket",
                        "analysis-results/duplicate/main.tf",
                        null
                )
        );
        when(orchestrator.executeProviderAndStoreDraft(any(AnalysisJobEntity.class))).thenReturn(execution);
        when(orchestrator.registerGeneratedTerraform(anyLong(), any(AnalysisJobExecution.class)))
                .thenReturn(mock(ProjectFileEntity.class));
        doAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            entity.setStatus(AnalysisJobStatus.SUCCEEDED);
            entity.setResultObjectKey(execution.writeResult().key());
            return null;
        }).when(orchestrator).markSucceeded(
                any(AnalysisJobEntity.class),
                any(AnalysisResult.class),
                any(ObjectWriteResult.class),
                any(ProjectFileEntity.class)
        );

        AnalysisJobRunner runner = new AnalysisJobRunner(
                orchestrator,
                stateService,
                new AnalysisObservability(new SimpleMeterRegistry())
        );

        runner.run(jobId);
        runner.run(jobId);

        assertThat(statusBeforeMarkRunning)
                .containsExactly(AnalysisJobStatus.PENDING, AnalysisJobStatus.SUCCEEDED);
        assertThat(repository.findById(jobId))
                .get()
                .extracting(AnalysisJobEntity::getStatus)
                .isEqualTo(AnalysisJobStatus.SUCCEEDED);

        verify(orchestrator, times(2)).executeProviderAndStoreDraft(any(AnalysisJobEntity.class));
        verify(orchestrator, times(2)).registerGeneratedTerraform(anyLong(), any(AnalysisJobExecution.class));
    }
}
