package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
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

    @MockBean
    private ProjectArtifactService projectArtifactService;

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

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new AnalysisJobRunner(
                orchestrator,
                stateService,
                new AnalysisObservability(registry)
        ).run(jobId);

        AnalysisJobEntity persisted = repository.findById(jobId).orElseThrow();

        assertThat(persisted.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(persisted.getFailureReason()).isEqualTo(AnalysisJobRunner.GENERIC_FAILURE_REASON);
        assertThat(persisted.getResultFileId()).isNull();
        assertThat(persisted.getResultObjectKey()).isNull();

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

        assertThat(registry.find("terraformers.analysis.jobs")
                .tags("outcome", "failed").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("terraformers.analysis.failures")
                .tags("category", "other").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("terraformers.analysis.duration").timer().count()).isEqualTo(1);

        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("analysisJobId=" + jobId)
                .contains("Analysis job failed outcome=failed exceptionCategory=other")
                .contains("Compensated stored analysis draft after relational finalization failure")
                .contains("trace_id= span_id=");
    }

    @TestConfiguration
    static class BaselineConfig {

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

    static class CapturingObjectWriter implements ObjectWriter, ObjectRemover {

        private final List<ObjectWriteRequest> writes = new ArrayList<>();
        private final List<ObjectReference> removals = new ArrayList<>();
        private final Map<String, String> objects = new LinkedHashMap<>();

        @Override
        public void remove(ObjectReference reference) {
            removals.add(reference);
            objects.remove(reference.bucket() + "/" + reference.key());
        }

        @Override
        public ObjectWriteResult writeText(ObjectWriteRequest request) {
            writes.add(request);
            objects.put(request.bucket() + "/" + request.key(), request.content());
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
    }
}
