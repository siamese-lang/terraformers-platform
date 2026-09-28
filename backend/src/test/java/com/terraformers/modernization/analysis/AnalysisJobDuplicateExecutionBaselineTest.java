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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@Import(AnalysisJobStateService.class)
@ExtendWith(OutputCaptureExtension.class)
class AnalysisJobDuplicateExecutionBaselineTest {

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private AnalysisJobStateService stateService;

    @MockBean
    private AnalysisJobOrchestrator orchestrator;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicateDeliveryDoesNotReexecuteSucceededJobAfterAtomicClaim(CapturedOutput output) {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(701L);
        job.setSourceFileId(801L);
        job.setSourceBucket("duplicate-baseline-bucket");
        job.setSourceKey("source/duplicate-baseline.png");
        job.setCorrelationId("duplicate-delivery");
        job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        job.setStatus(AnalysisJobStatus.PENDING);
        String jobId = repository.saveAndFlush(job).getId();

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

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalysisJobRunner runner = TestAnalysisJobRunnerFactory.create(
                orchestrator, stateService, new AnalysisObservability(registry)
        );

        runner.run(jobId);
        runner.run(jobId);

        assertThat(repository.findById(jobId))
                .get()
                .extracting(AnalysisJobEntity::getStatus)
                .isEqualTo(AnalysisJobStatus.SUCCEEDED);

        verify(orchestrator, times(1)).markRunning(any(AnalysisJobEntity.class));
        verify(orchestrator, times(1)).executeProviderAndStoreDraft(any(AnalysisJobEntity.class));
        verify(orchestrator, times(1)).registerGeneratedTerraform(anyLong(), any(AnalysisJobExecution.class));
        assertThat(registry.find("terraformers.analysis.claims")
                .tags("outcome", "initial_claim").counter().count()).isEqualTo(1);
        assertThat(registry.find("terraformers.analysis.claims")
                .tags("outcome", "not_claimed").counter().count()).isEqualTo(1);
        assertThat(registry.find("terraformers.analysis.queue.wait").timer().count()).isEqualTo(1);
        assertThat(output.getOut() + output.getErr())
                .contains("analysisJobId=" + jobId)
                .contains("Analysis job claimed outcome=initial_claim")
                .contains("Analysis job execution started")
                .contains("Analysis job skipped outcome=not_claimed");
    }
}
