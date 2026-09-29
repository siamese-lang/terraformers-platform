package com.terraformers.modernization.analysis;

import com.terraformers.modernization.projectcore.ProjectArtifactService;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.ObjectReference;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.stereotype.Service;

@Service
public class AnalysisJobOrchestrator {

    private final AnalysisProvider analysisProvider;
    private final ProgressPublisher progressPublisher;
    private final AnalysisResultStorage resultStorage;
    private final ProjectArtifactService projectArtifactService;
    private final TerraformDraftValidator terraformDraftValidator;

    public AnalysisJobOrchestrator(
            AnalysisProvider analysisProvider,
            ProgressPublisher progressPublisher,
            AnalysisResultStorage resultStorage,
            ProjectArtifactService projectArtifactService,
            TerraformDraftValidator terraformDraftValidator
    ) {
        this.analysisProvider = analysisProvider;
        this.progressPublisher = progressPublisher;
        this.resultStorage = resultStorage;
        this.projectArtifactService = projectArtifactService;
        this.terraformDraftValidator = terraformDraftValidator;
    }

    public AnalysisResult executeProviderAndValidate(AnalysisJobEntity entity) {
        AnalysisResult result;
        try {
            result = analysisProvider.analyze(toContext(entity));
        } catch (AnalysisProviderTimeoutException exception) {
            throw exception;
        } catch (AnalysisProviderFailureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (hasStandardNetworkTimeout(exception)) {
                throw new AnalysisProviderTimeoutException(exception);
            }
            throw exception;
        }
        TerraformDraftValidation validation = terraformDraftValidator.validate(result.terraformCode());
        if (!validation.valid()) {
            throw new IllegalStateException(validation.reason());
        }
        return result.withTerraformCode(validation.sanitizedContent());
    }

    private boolean hasStandardNetworkTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) return true;
            current = current.getCause();
        }
        return false;
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
        progressPublisher.publish(ProgressEvent.of(entity, AnalysisJobStatus.SUCCEEDED, "analysis job completed"));
    }

    public void markFailed(AnalysisJobEntity entity, RuntimeException exception) {
        markFailed(entity, exception.getMessage());
    }

    public void markFailed(AnalysisJobEntity entity, String failureReason) {
        entity.setStatus(AnalysisJobStatus.FAILED);
        entity.setFailureReason(safeFailureReason(failureReason));
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
