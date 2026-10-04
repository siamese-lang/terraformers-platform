package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.terraformers.modernization.analysis.bedrock.ArchitectureInputRejectedException;
import com.terraformers.modernization.analysis.bedrock.ArchitectureInputType;
import com.terraformers.modernization.projectcore.ProjectArtifactService;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.StubObjectWriter;
import java.util.ArrayList;
import java.util.List;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class AnalysisJobOrchestratorTest {

    @Test
    void normalizesCausalNetworkTimeoutOnlyAtProviderBoundary() {
        RuntimeException wrappedTimeout = new RuntimeException("provider transport",
                new SocketTimeoutException("read timed out"));
        AnalysisJobOrchestrator orchestrator = orchestrator(context -> { throw wrappedTimeout; },
                mock(AnalysisResultStorage.class));

        assertThatThrownBy(() -> orchestrator.executeProviderAndValidate(sampleEntity(100L)))
                .isInstanceOf(AnalysisProviderTimeoutException.class)
                .hasCause(wrappedTimeout);
    }

    @Test
    void storageRemainsOutsideProviderBoundary() {
        AnalysisProvider provider = context -> validResult();
        AnalysisResultStorage storage = mock(AnalysisResultStorage.class);
        RuntimeException storageTimeout = new RuntimeException("storage transport",
                new SocketTimeoutException("write timed out"));
        when(storage.storeTerraformDraft(any(), any())).thenThrow(storageTimeout);
        AnalysisJobOrchestrator orchestrator = orchestrator(provider, storage);

        assertThatThrownBy(() -> orchestrator.storeTerraformDraft(
                new com.terraformers.modernization.storage.ObjectReference("bucket", "key"), validResult()))
                .isSameAs(storageTimeout)
                .isNotInstanceOf(AnalysisProviderTimeoutException.class);
        verify(storage).storeTerraformDraft(any(), any());
    }

    @Test
    void preservesExplicitProviderSemanticFailureWithNestedNetworkTimeout() {
        AnalysisProviderFailureException semanticFailure = new AnalysisProviderFailureException(
                AnalysisProviderFailureReason.OUTPUT_TRUNCATED,
                new SocketTimeoutException("nested timeout"));
        AnalysisJobOrchestrator orchestrator = orchestrator(context -> { throw semanticFailure; },
                mock(AnalysisResultStorage.class));

        assertThatThrownBy(() -> orchestrator.executeProviderAndValidate(sampleEntity(100L)))
                .isSameAs(semanticFailure)
                .isNotInstanceOf(AnalysisProviderTimeoutException.class);
    }

    @Test
    void storesResultObjectKeyAndFileIdWhenAnalysisSucceeds() {
        AnalysisProvider provider = context -> new AnalysisResult(
                "test-provider",
                "resource \"aws_s3_bucket\" \"accepted\" { bucket_prefix = \"accepted-\" }",
                "test explanation",
                List.of("S3"),
                List.of("upload artifacts are stored in S3"),
                List.of(),
                List.of("reference-1")
        );
        CapturingProgressPublisher progressPublisher = new CapturingProgressPublisher();
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setResultBucketName("result-bucket");
        properties.setResultKeyPrefix("test-results");
        AnalysisResultStorage resultStorage = new AnalysisResultStorage(new StubObjectWriter(), properties);
        ProjectArtifactService artifactService = mock(ProjectArtifactService.class);
        ProjectFileEntity resultFile = mock(ProjectFileEntity.class);
        when(resultFile.getFileId()).thenReturn(301L);
        when(artifactService.registerGeneratedTerraform(anyLong(), anyString(), any(ObjectWriteResult.class)))
                .thenReturn(resultFile);
        AnalysisJobOrchestrator orchestrator = new AnalysisJobOrchestrator(
                provider,
                progressPublisher,
                resultStorage,
                artifactService,
                new TerraformDraftValidator(),
                passThroughExecutableValidator()
        );

        AnalysisJobEntity job = sampleEntity(101L);

        orchestrator.markRunning(job);
        AnalysisResult result = orchestrator.executeProviderAndValidate(job);
        var reference = orchestrator.resolveResultObjectReference(job);
        ObjectWriteResult writeResult = orchestrator.storeTerraformDraft(reference, result);
        ProjectFileEntity registered = orchestrator.registerGeneratedTerraform(job.getProjectId(), result, writeResult);
        orchestrator.markSucceeded(job, result, writeResult, registered);

        assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
        assertThat(job.getProvider()).isEqualTo("test-provider");
        assertThat(job.getResultFileId()).isEqualTo(301L);
        assertThat(job.getResultObjectKey()).startsWith("test-results/101/");
        assertThat(job.getResultObjectKey()).endsWith("/" + job.getId() + "/main.tf");
        assertThat(job.getResultPreview()).contains("resource \"aws_s3_bucket\"");
        verify(artifactService).registerGeneratedTerraform(
                101L,
                "resource \"aws_s3_bucket\" \"accepted\" { bucket_prefix = \"accepted-\" }",
                new ObjectWriteResult("metadata-only", false, "result-bucket", job.getResultObjectKey(), null)
        );
        assertThat(progressPublisher.statuses()).containsExactly(
                AnalysisJobStatus.RUNNING,
                AnalysisJobStatus.SUCCEEDED
        );
    }

    @Test
    void executableValidationRunsAfterSafetySanitizationAndBlocksFinalization() {
        AnalysisProvider provider = context -> new AnalysisResult(
                "test-provider",
                """
                ```hcl
                resource "aws_s3_bucket" "accepted" {
                  bucket_prefix = "accepted-"
                }
                ```
                """,
                "test explanation",
                List.of("S3"),
                List.of(),
                List.of(),
                List.of()
        );
        TerraformExecutableValidator executableValidator = mock(TerraformExecutableValidator.class);
        TerraformDiagnosticSummary diagnosticSummary = new TerraformDiagnosticSummary(List.of(
                TerraformDiagnosticSummary.DiagnosticClass.MISSING_REQUIRED_ARGUMENT), 1, 0);
        when(executableValidator.validate(anyString())).thenReturn(
                new TerraformDraftValidation(false, "",
                        "VALIDATE_CONFIGURATION: generated Terraform failed Terraform CLI validation",
                        diagnosticSummary));
        AnalysisJobOrchestrator orchestrator = new AnalysisJobOrchestrator(
                provider,
                mock(ProgressPublisher.class),
                mock(AnalysisResultStorage.class),
                mock(ProjectArtifactService.class),
                new TerraformDraftValidator(),
                executableValidator
        );

        assertThatThrownBy(() -> orchestrator.executeProviderAndValidate(sampleEntity(105L)))
                .isInstanceOfSatisfying(TerraformValidationFailureException.class, failure -> {
                    assertThat(failure.category()).isEqualTo(
                            TerraformValidationFailureException.Category.VALIDATE_CONFIGURATION);
                    assertThat(failure).hasMessage(
                            "VALIDATE_CONFIGURATION: generated Terraform failed Terraform CLI validation");
                    assertThat(failure.diagnosticSummary()).isEqualTo(diagnosticSummary);
                });

        verify(executableValidator).validate("""
                resource "aws_s3_bucket" "accepted" {
                  bucket_prefix = "accepted-"
                }
                """.strip());
    }

    @Test
    void marksFailedWhenAnalysisProviderFails() {
        AnalysisProvider provider = context -> {
            throw new IllegalStateException("provider failure");
        };
        CapturingProgressPublisher progressPublisher = new CapturingProgressPublisher();
        AnalysisResultStorage resultStorage = new AnalysisResultStorage(new StubObjectWriter(), new AnalysisRuntimeProperties());
        ProjectArtifactService artifactService = mock(ProjectArtifactService.class);
        AnalysisJobOrchestrator orchestrator = new AnalysisJobOrchestrator(
                provider,
                progressPublisher,
                resultStorage,
                artifactService,
                new TerraformDraftValidator(),
                passThroughExecutableValidator()
        );

        AnalysisJobEntity job = sampleEntity(102L);

        assertThatThrownBy(() -> orchestrator.executeProviderAndValidate(job))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("provider failure");
        assertThat(job.getResultFileId()).isNull();
        assertThat(job.getResultObjectKey()).isNull();
        verify(artifactService, never()).registerGeneratedTerraform(anyLong(), anyString(), any(ObjectWriteResult.class));
    }

    @Test
    void marksFailedAndDoesNotRegisterTerraformWhenProviderReturnsProviderOnlyCode() {
        AnalysisProvider provider = context -> new AnalysisResult(
                "test-provider",
                "provider \"aws\" { region = var.aws_region }",
                "provider only",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        CapturingProgressPublisher progressPublisher = new CapturingProgressPublisher();
        AnalysisResultStorage resultStorage = new AnalysisResultStorage(new StubObjectWriter(), new AnalysisRuntimeProperties());
        ProjectArtifactService artifactService = mock(ProjectArtifactService.class);
        AnalysisJobOrchestrator orchestrator = new AnalysisJobOrchestrator(
                provider,
                progressPublisher,
                resultStorage,
                artifactService,
                new TerraformDraftValidator(),
                passThroughExecutableValidator()
        );

        AnalysisJobEntity job = sampleEntity(103L);

        assertThatThrownBy(() -> orchestrator.executeProviderAndValidate(job))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("resource or module");
        assertThat(job.getResultObjectKey()).isNull();
        verify(artifactService, never()).registerGeneratedTerraform(anyLong(), anyString(), any(ObjectWriteResult.class));
    }

    @Test
    void propagatesRejectedInputWithoutValidatingOrStoringTerraform() {
        ArchitectureInputRejectedException rejection = new ArchitectureInputRejectedException(
                ArchitectureInputType.NON_ARCHITECTURE_IMAGE, 0.98);
        AnalysisProvider provider = context -> { throw rejection; };
        TerraformDraftValidator validator = mock(TerraformDraftValidator.class);
        TerraformExecutableValidator executableValidator = mock(TerraformExecutableValidator.class);
        AnalysisResultStorage resultStorage = mock(AnalysisResultStorage.class);
        ProjectArtifactService artifactService = mock(ProjectArtifactService.class);
        AnalysisJobOrchestrator orchestrator = new AnalysisJobOrchestrator(
                provider,
                mock(ProgressPublisher.class),
                resultStorage,
                artifactService,
                validator,
                executableValidator
        );

        assertThatThrownBy(() -> orchestrator.executeProviderAndValidate(sampleEntity(104L)))
                .isSameAs(rejection);

        verifyNoInteractions(validator, executableValidator, resultStorage, artifactService);
    }

    private AnalysisJobEntity sampleEntity(Long projectId) {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(projectId);
        job.setSourceFileId(201L);
        job.setSourceBucket("source-bucket");
        job.setSourceKey("uploads/diagram.png");
        job.setCorrelationId("corr-1");
        job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        job.prePersist();
        return job;
    }

    private AnalysisJobOrchestrator orchestrator(AnalysisProvider provider, AnalysisResultStorage storage) {
        return new AnalysisJobOrchestrator(
                provider,
                mock(ProgressPublisher.class),
                storage,
                mock(ProjectArtifactService.class),
                new TerraformDraftValidator(),
                passThroughExecutableValidator()
        );
    }

    private TerraformExecutableValidator passThroughExecutableValidator() {
        return candidate -> new TerraformDraftValidation(true, candidate, null);
    }

    private AnalysisResult validResult() {
        String terraform = """
                resource "null_resource" "valid" {
                  triggers = {
                    source = "storage-boundary-test"
                  }
                }
                """;
        return new AnalysisResult("test", terraform, "summary",
                List.of(), List.of(), List.of(), List.of());
    }

    private static class CapturingProgressPublisher implements ProgressPublisher {
        private final List<ProgressEvent> events = new ArrayList<>();

        @Override
        public void publish(ProgressEvent event) {
            events.add(event);
        }

        List<AnalysisJobStatus> statuses() {
            return events.stream().map(ProgressEvent::status).toList();
        }
    }
}
