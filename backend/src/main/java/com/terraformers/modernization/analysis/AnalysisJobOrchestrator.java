package com.terraformers.modernization.analysis;

import com.terraformers.modernization.projectcore.ProjectArtifactService;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.ObjectReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalysisJobOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobOrchestrator.class);

    private final AnalysisProvider analysisProvider;
    private final ProgressPublisher progressPublisher;
    private final AnalysisResultStorage resultStorage;
    private final ProjectArtifactService projectArtifactService;
    private final TerraformDraftValidator terraformDraftValidator;
    private final TerraformExecutableValidator terraformExecutableValidator;

    @Autowired
    public AnalysisJobOrchestrator(
            AnalysisProvider analysisProvider,
            ProgressPublisher progressPublisher,
            AnalysisResultStorage resultStorage,
            ProjectArtifactService projectArtifactService,
            TerraformDraftValidator terraformDraftValidator,
            TerraformExecutableValidator terraformExecutableValidator
    ) {
        this.analysisProvider = analysisProvider;
        this.progressPublisher = progressPublisher;
        this.resultStorage = resultStorage;
        this.projectArtifactService = projectArtifactService;
        this.terraformDraftValidator = terraformDraftValidator;
        this.terraformExecutableValidator = terraformExecutableValidator;
    }

    public AnalysisResult executeProviderAndValidate(AnalysisJobEntity entity) {
        AnalysisResult result;
        AnalysisDiagnosticEvidence.stage("provider");
        try {
            result = analysisProvider.analyze(toContext(entity));
        } catch (AnalysisProviderTimeoutException exception) {
            throw exception;
        } catch (AnalysisProviderFailureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (ProviderFailureClassifier.isTimeout(exception)) {
                throw new AnalysisProviderTimeoutException(exception);
            }
            throw exception;
        }
        log.info("Analysis provider completed provider={}", result.provider());

        var diagnostic = AnalysisDiagnosticEvidence.current();
        if (diagnostic != null) {
            // Non-Vertex providers do not expose original vision/initial candidates; do not invent them.
            diagnostic.candidate("final", result.terraformCode());
        }
        AnalysisDiagnosticEvidence.stage("draft_validation");
        TerraformDraftValidation validation = terraformDraftValidator.validate(result.terraformCode());
        if (!validation.valid()) {
            log.warn("Terraform draft validation failed reason={}", validation.reason());
            throw new IllegalStateException(validation.reason());
        }
        if (diagnostic != null) diagnostic.captured("draft_validation");
        log.info("Terraform draft validation passed");

        if (diagnostic != null) diagnostic.candidate("validated", validation.sanitizedContent());
        AnalysisDiagnosticEvidence.stage("cli_init");
        TerraformDraftValidation executableValidation =
                terraformExecutableValidator.validate(validation.sanitizedContent());
        if (diagnostic != null) diagnostic.cli(executableValidation);
        if (!executableValidation.valid()) {
            log.warn("Terraform executable validation failed reason={}", executableValidation.reason());
            throw TerraformValidationFailureException.fromSafeReason(
                    executableValidation.reason(), executableValidation.diagnosticSummary());
        }
        log.info("Terraform executable validation passed");
        return result.withTerraformCode(executableValidation.sanitizedContent());
    }

    public ObjectReference resolveResultObjectReference(AnalysisJobEntity entity) {
        return resultStorage.resolveResultObjectReference(entity);
    }

    public ObjectWriteResult storeTerraformDraft(ObjectReference reference, AnalysisResult result) {
        return resultStorage.storeTerraformDraft(reference, result);
    }

    public void removeStoredDraft(ObjectReference reference) {
        resultStorage.removeStoredDraft(reference);
    }

    public ProjectFileEntity registerGeneratedTerraform(Long projectId, AnalysisResult result,
            ObjectWriteResult writeResult) {
        return projectArtifactService.registerGeneratedTerraform(
                projectId,
                result.terraformCode(),
                writeResult
        );
    }

    public void markRunning(AnalysisJobEntity entity) {
        entity.setStatus(AnalysisJobStatus.RUNNING);
        progressPublisher.publish(ProgressEvent.of(entity, AnalysisJobStatus.RUNNING, "analysis job started"));
    }

    public void markSucceeded(
            AnalysisJobEntity entity,
            AnalysisResult result,
            ObjectWriteResult writeResult,
            ProjectFileEntity resultFile
    ) {
        entity.setStatus(AnalysisJobStatus.SUCCEEDED);
        entity.setProvider(result.provider());
        entity.setResultFileId(resultFile.getFileId());
        entity.setResultObjectKey(writeResult.key());
        entity.setResultPreview(result.preview());
        entity.setAnalysisSummary(result.explanation());
        entity.setDetectedComponents(String.join("\n", result.components() == null ? java.util.List.of() : result.components()));
        entity.setDetectedRelationships(String.join("\n", result.relationships() == null ? java.util.List.of() : result.relationships()));
        entity.setAnalysisWarnings(String.join("\n", result.warnings() == null ? java.util.List.of() : result.warnings()));
        entity.setTerminalAt(java.time.Instant.now());
    }

    public void publishSucceededProgress(AnalysisJobEntity entity) {
        progressPublisher.publish(ProgressEvent.of(entity, AnalysisJobStatus.SUCCEEDED, "analysis job completed"));
    }

    public void markFailed(AnalysisJobEntity entity, RuntimeException exception) {
        markFailed(entity, exception.getMessage());
    }

    public void markFailed(AnalysisJobEntity entity, String failureReason) {
        entity.setStatus(AnalysisJobStatus.FAILED);
        entity.setFailureReason(safeFailureReason(failureReason));
        entity.setTerminalAt(java.time.Instant.now());
        publishFailedProgress(entity);
    }

    public void publishFailedProgress(AnalysisJobEntity entity) {
        progressPublisher.publish(ProgressEvent.of(entity, AnalysisJobStatus.FAILED, "analysis job failed"));
    }

    private String safeFailureReason(String failureReason) {
        String reason = failureReason == null || failureReason.isBlank()
                ? "analysis job failed"
                : failureReason.strip();
        return reason.length() <= 2000 ? reason : reason.substring(0, 2000);
    }

    private AnalysisRequestContext toContext(AnalysisJobEntity entity) {
        return new AnalysisRequestContext(
                entity.getId(),
                String.valueOf(entity.getProjectId()),
                entity.getSourceBucket(),
                entity.getSourceKey(),
                entity.getCorrelationId(),
                entity.getAnalysisMode()
        );
    }
}
