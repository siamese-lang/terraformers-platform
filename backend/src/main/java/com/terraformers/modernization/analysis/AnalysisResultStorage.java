package com.terraformers.modernization.analysis;

import com.terraformers.modernization.storage.ObjectRemover;
import com.terraformers.modernization.storage.ObjectReference;
import com.terraformers.modernization.storage.ObjectWriteRequest;
import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.ObjectWriter;
import org.springframework.stereotype.Service;

@Service
public class AnalysisResultStorage {

    private final ObjectWriter objectWriter;
    private final AnalysisRuntimeProperties properties;

    public AnalysisResultStorage(ObjectWriter objectWriter, AnalysisRuntimeProperties properties) {
        this.objectWriter = objectWriter;
        this.properties = properties;
    }

    public void removeStoredDraft(ObjectReference reference) {
        if (!(objectWriter instanceof ObjectRemover remover)) {
            throw new IllegalStateException("persistent object writer does not support idempotent removal");
        }
        remover.remove(reference);
    }

    public ObjectReference resolveResultObjectReference(AnalysisJobEntity job) {
        String intentBucket = job.getResultObjectIntentBucket();
        String intentKey = job.getResultObjectIntentKey();
        if (intentBucket != null && intentKey != null) {
            return new ObjectReference(intentBucket, intentKey);
        }
        if (intentBucket != null || intentKey != null) {
            throw new IllegalStateException("analysis result object intent is partially populated");
        }
        return new ObjectReference(resolveResultBucket(job), buildResultKey(job));
    }

    public ObjectWriteResult storeTerraformDraft(ObjectReference reference, AnalysisResult result) {
        ObjectWriteResult writeResult = objectWriter.writeText(new ObjectWriteRequest(
                reference.bucket(),
                reference.key(),
                result.terraformCode(),
                "text/plain; charset=utf-8"
        ));
        if (!reference.bucket().equals(writeResult.bucket()) || !reference.key().equals(writeResult.key())) {
            throw new IllegalStateException("object writer relocated the requested analysis result");
        }
        return writeResult;
    }

    private String resolveResultBucket(AnalysisJobEntity job) {
        if (properties.getResultBucketName() != null && !properties.getResultBucketName().isBlank()) {
            return properties.getResultBucketName();
        }
        return job.getSourceBucket();
    }

    private String buildResultKey(AnalysisJobEntity job) {
        String prefix = normalizePrefix(properties.getResultKeyPrefix());
        return prefix + "/" + job.getProjectId() + "/" + job.getId() + "/main.tf";
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "analysis-results";
        }
        String normalized = prefix.strip();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "analysis-results" : normalized;
    }
}
