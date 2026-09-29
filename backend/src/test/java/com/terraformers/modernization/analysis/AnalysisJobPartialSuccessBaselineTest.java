package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.projectcore.ProjectArtifactService;
import com.terraformers.modernization.storage.ObjectReference;
import com.terraformers.modernization.storage.ObjectRemover;
import com.terraformers.modernization.storage.ObjectWriteRequest;
import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.ObjectWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import({
        AnalysisJobStateService.class,
        AnalysisJobOrchestrator.class,
        AnalysisJobPartialSuccessBaselineTest.BaselineConfig.class
})
class AnalysisJobPartialSuccessBaselineTest {

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private AnalysisJobStateService stateService;

    @Autowired
    private AnalysisJobOrchestrator orchestrator;

    @Autowired
    private CapturingObjectWriter objectWriter;

    @Autowired
    private AnalysisObservability observability;

    @Autowired
    private SimpleMeterRegistry meterRegistry;

    @MockBean
    private ProjectArtifactService projectArtifactService;

    @BeforeEach
    void resetFixtureState() {
        objectWriter.reset();
        meterRegistry.clear();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void successfulObjectWriteIsCompensatedWhenRelationalFinalizationFails(CapturedOutput output) {
        when(projectArtifactService.registerGeneratedTerraform(
                anyLong(),
                anyString(),
                any(ObjectWriteResult.class)
        )).thenThrow(new IllegalStateException("forced relational finalization failure"));

        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(901L);
        job.setSourceFileId(902L);
        job.setSourceBucket("partial-success-source");
        job.setSourceKey("source/architecture.png");
        job.setCorrelationId("partial-success");
        job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        job.setStatus(AnalysisJobStatus.PENDING);
        String jobId = repository.saveAndFlush(job).getId();

        TestAnalysisJobRunnerFactory.create(
                orchestrator, stateService, observability
        ).run(jobId);

        AnalysisJobEntity persisted = repository.findById(jobId).orElseThrow();

        assertThat(persisted.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(persisted.getFailureReason()).isEqualTo(AnalysisJobRunner.GENERIC_FAILURE_REASON);
        assertThat(persisted.getResultFileId()).isNull();
        assertThat(persisted.getResultObjectKey()).isNull();
        assertThat(persisted.getResultObjectIntentBucket()).isEqualTo("partial-success-result");
        assertThat(persisted.getResultObjectIntentKey()).endsWith("/" + jobId + "/main.tf");
        assertThat(persisted.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.COMPLETED);

        assertThat(objectWriter.writes()).hasSize(1);
        ObjectWriteRequest write = objectWriter.writes().get(0);
        assertThat(write.bucket()).isEqualTo("partial-success-result");
        assertThat(write.key()).endsWith("/" + jobId + "/main.tf");
        assertThat(write.content()).contains("resource \"aws_s3_bucket\" \"partial_success\"");
        assertThat(write.content()).contains("bucket_prefix = \"partial-success-\"");
        assertThat(objectWriter.objects()).isEmpty();
        assertThat(objectWriter.removals())
                .containsExactly(new ObjectReference(write.bucket(), write.key()));

        verify(projectArtifactService).registerGeneratedTerraform(
                anyLong(),
                anyString(),
                any(ObjectWriteResult.class)
        );

        assertThat(observabilityRegistry().find("terraformers.analysis.jobs")
                .tags("outcome", "failed").counter().count()).isEqualTo(1.0);
        assertThat(observabilityRegistry().find("terraformers.analysis.failures")
                .tags("category", "result_finalization").counter().count()).isEqualTo(1.0);
        assertThat(observabilityRegistry().find("terraformers.analysis.duration").timer().count()).isEqualTo(1);
        assertThat(observabilityRegistry().find("terraformers.analysis.stage.duration")
                .tags("stage", "analysis_execution", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(observabilityRegistry().find("terraformers.analysis.stage.duration")
                .tags("stage", "result_finalize", "outcome", "failure")
                .timer().count()).isEqualTo(1);
        assertThat(observabilityRegistry().find("terraformers.analysis.stage.duration")
                .tags("stage", "compensation", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(observabilityRegistry().find("terraformers.analysis.stage.failures")
                .tags("stage", "result_finalize", "category", "result_finalization")
                .counter().count()).isEqualTo(1.0);

        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("analysisJobId=" + jobId)
                .contains("Analysis job failed outcome=failed exceptionCategory=result_finalization")
                .containsSubsequence(
                        "analysis stage outcome=success stage=analysis_execution",
                        "analysis stage outcome=success stage=compensation",
                        "analysis stage outcome=failure stage=result_finalize category=result_finalization"
                );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedCompensationLeavesDurablyAccountedObjectResidue(CapturedOutput output) {
        when(projectArtifactService.registerGeneratedTerraform(
                anyLong(),
                anyString(),
                any(ObjectWriteResult.class)
        )).thenThrow(new IllegalStateException("forced relational finalization failure"));
        objectWriter.failRemovals();

        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(911L);
        job.setSourceFileId(912L);
        job.setSourceBucket("compensation-failure-source");
        job.setSourceKey("source/architecture.png");
        job.setCorrelationId("compensation-failure");
        job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        job.setStatus(AnalysisJobStatus.PENDING);
        String jobId = repository.saveAndFlush(job).getId();
        TestAnalysisJobRunnerFactory.create(orchestrator, stateService, observability).run(jobId);

        AnalysisJobEntity persisted = repository.findById(jobId).orElseThrow();
        ObjectWriteRequest write = objectWriter.writes().get(0);
        assertThat(persisted.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(persisted.getResultFileId()).isNull();
        assertThat(persisted.getResultObjectKey()).isNull();
        assertThat(persisted.getResultObjectIntentBucket()).isEqualTo(write.bucket());
        assertThat(persisted.getResultObjectIntentKey()).isEqualTo(write.key());
        assertThat(persisted.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.PENDING);
        assertThat(objectWriter.removals()).containsExactly(new ObjectReference(write.bucket(), write.key()));
        assertThat(objectWriter.objects()).containsKey(write.bucket() + "/" + write.key());
        assertThat(observabilityRegistry().find("terraformers.analysis.stage.duration")
                .tags("stage", "compensation", "outcome", "failure").timer().count()).isEqualTo(1);
        assertThat(observabilityRegistry().find("terraformers.analysis.stage.failures")
                .tags("stage", "compensation", "category", "other").counter().count()).isEqualTo(1.0);
        assertThat(output.getOut() + output.getErr())
                .contains("analysisJobId=" + jobId)
                .contains("analysis stage outcome=failure stage=compensation category=other")
                .doesNotContain(write.bucket())
                .doesNotContain(write.key());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistedThenThrownWriteIsRemovedUsingDurableIntent() {
        objectWriter.throwAfterWrite();
        AnalysisJobEntity job = pendingJob(921L, "ambiguous-write");
        String jobId = repository.saveAndFlush(job).getId();

        TestAnalysisJobRunnerFactory.create(orchestrator, stateService, observability).run(jobId);

        AnalysisJobEntity persisted = repository.findById(jobId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(persisted.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.COMPLETED);
        assertThat(objectWriter.objects()).isEmpty();
        assertThat(objectWriter.removals()).containsExactly(new ObjectReference(
                persisted.getResultObjectIntentBucket(), persisted.getResultObjectIntentKey()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistedThenThrownWriteRemainsDurablyAccountedWhenCleanupFails() {
        objectWriter.throwAfterWrite();
        objectWriter.failRemovals();
        AnalysisJobEntity job = pendingJob(926L, "ambiguous-write-cleanup-failure");
        String jobId = repository.saveAndFlush(job).getId();

        TestAnalysisJobRunnerFactory.create(orchestrator, stateService, observability).run(jobId);

        AnalysisJobEntity persisted = repository.findById(jobId).orElseThrow();
        ObjectWriteRequest write = objectWriter.writes().get(0);
        assertThat(persisted.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(persisted.getResultFileId()).isNull();
        assertThat(persisted.getResultObjectKey()).isNull();
        assertThat(persisted.getResultObjectIntentBucket()).isEqualTo(write.bucket());
        assertThat(persisted.getResultObjectIntentKey()).isEqualTo(write.key());
        assertThat(persisted.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.PENDING);
        assertThat(objectWriter.objects()).containsKey(write.bucket() + "/" + write.key());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recoveryDeletesDurablyAccountedResidueWithoutRetryingJob() {
        when(projectArtifactService.registerGeneratedTerraform(anyLong(), anyString(), any(ObjectWriteResult.class)))
                .thenThrow(new IllegalStateException("forced relational finalization failure"));
        objectWriter.failRemovals();
        AnalysisJobEntity job = pendingJob(931L, "cleanup-recovery");
        String jobId = repository.saveAndFlush(job).getId();
        TestAnalysisJobRunnerFactory.create(orchestrator, stateService, observability).run(jobId);
        AnalysisJobEntity pending = repository.findById(jobId).orElseThrow();
        ObjectReference reference = new ObjectReference(
                pending.getResultObjectIntentBucket(), pending.getResultObjectIntentKey());
        java.time.Instant deferredAt = java.time.Instant.parse("2099-01-01T00:00:00Z");

        assertThatThrownBy(() -> stateService.recoverPendingCleanup(jobId))
                .isInstanceOf(AnalysisJobStateService.CleanupRecoveryException.class);
        assertThat(stateService.deferPendingCleanup(jobId, reference, deferredAt)).isTrue();
        AnalysisJobEntity deferred = repository.findById(jobId).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(deferred.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.PENDING);
        assertThat(deferred.getUpdatedAt()).isEqualTo(deferredAt);
        assertThat(objectWriter.objects()).containsKey(reference.bucket() + "/" + reference.key());
        objectWriter.allowRemovals();

        assertThat(stateService.recoverPendingCleanup(jobId)).isTrue();

        AnalysisJobEntity recovered = repository.findById(jobId).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(recovered.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.COMPLETED);
        assertThat(objectWriter.objects()).isEmpty();
    }

    private AnalysisJobEntity pendingJob(Long projectId, String correlationId) {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(projectId); job.setSourceFileId(projectId + 1);
        job.setSourceBucket("source"); job.setSourceKey("source/architecture.png");
        job.setCorrelationId(correlationId); job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        job.setStatus(AnalysisJobStatus.PENDING);
        return job;
    }

    @TestConfiguration
    static class BaselineConfig {

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        AnalysisObservability analysisObservability(SimpleMeterRegistry registry) {
            return new AnalysisObservability(registry);
        }

        @Bean
        AnalysisProvider analysisProvider() {
            return context -> new AnalysisResult(
                    "partial-success-provider",
                    "resource \"aws_s3_bucket\" \"partial_success\" { bucket_prefix = \"partial-success-\" }",
                    "partial success baseline",
                    List.of("S3"),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        @Bean
        ProgressPublisher progressPublisher() {
            return event -> { };
        }

        @Bean
        CapturingObjectWriter capturingObjectWriter() {
            return new CapturingObjectWriter();
        }

        @Bean
        AnalysisResultStorage analysisResultStorage(
                CapturingObjectWriter writer,
                AnalysisRuntimeProperties properties
        ) {
            properties.setResultBucketName("partial-success-result");
            properties.setResultKeyPrefix("m5-partial-success");
            return new AnalysisResultStorage(writer, properties);
        }

        @Bean
        TerraformDraftValidator terraformDraftValidator() {
            return new TerraformDraftValidator();
        }
    }

    private SimpleMeterRegistry observabilityRegistry() {
        return meterRegistry;
    }

    static class CapturingObjectWriter implements ObjectWriter, ObjectRemover {

        private final List<ObjectWriteRequest> writes = new ArrayList<>();
        private final List<ObjectReference> removals = new ArrayList<>();
        private final Map<String, String> objects = new LinkedHashMap<>();
        private boolean failRemovals;
        private boolean throwAfterWrite;

        @Override
        public void remove(ObjectReference reference) {
            removals.add(reference);
            if (failRemovals) {
                throw new IllegalStateException("forced cleanup failure");
            }
            objects.remove(reference.bucket() + "/" + reference.key());
        }

        @Override
        public ObjectWriteResult writeText(ObjectWriteRequest request) {
            writes.add(request);
            objects.put(request.bucket() + "/" + request.key(), request.content());
            if (throwAfterWrite) {
                throw new IllegalStateException("ambiguous object write failure");
            }
            return new ObjectWriteResult(
                    "capturing-persisted",
                    true,
                    request.bucket(),
                    request.key(),
                    "m5-partial-success-etag"
            );
        }

        List<ObjectWriteRequest> writes() {
            return List.copyOf(writes);
        }

        List<ObjectReference> removals() {
            return List.copyOf(removals);
        }

        Map<String, String> objects() {
            return Map.copyOf(objects);
        }

        void failRemovals() {
            failRemovals = true;
        }

        void allowRemovals() { failRemovals = false; }

        void throwAfterWrite() { throwAfterWrite = true; }

        void reset() {
            writes.clear();
            removals.clear();
            objects.clear();
            failRemovals = false;
            throwAfterWrite = false;
        }
    }
}
