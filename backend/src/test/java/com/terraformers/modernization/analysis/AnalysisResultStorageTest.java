package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.ObjectReference;
import com.terraformers.modernization.storage.StubObjectWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisResultStorageTest {

    @Test
    void storesTerraformDraftAndReturnsGeneratedObjectKey() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setResultBucketName("result-bucket");
        properties.setResultKeyPrefix("custom-prefix/");

        AnalysisResultStorage storage = new AnalysisResultStorage(new StubObjectWriter(), properties);
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(101L);
        job.setSourceFileId(201L);
        job.setSourceBucket("source-bucket");
        job.setSourceKey("uploads/diagram.png");
        job.prePersist();

        ObjectReference reference = storage.resolveResultObjectReference(job);
        ObjectWriteResult writeResult = storage.storeTerraformDraft(reference, new AnalysisResult(
                "stub",
                "provider \"aws\" {}",
                "explanation",
                List.of("component"),
                List.of("relationship"),
                List.of(),
                List.of("reference-1")
        ));

        assertThat(writeResult.bucket()).isEqualTo("result-bucket");
        assertThat(writeResult.key()).isEqualTo("custom-prefix/101/" + job.getId() + "/main.tf");
        assertThat(writeResult.provider()).isEqualTo("metadata-only");
        assertThat(writeResult.persisted()).isFalse();
        assertThat(writeResult.eTag()).isNull();
    }

    @Test
    void fallsBackToSourceBucketWhenResultBucketIsNotConfigured() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        AnalysisResultStorage storage = new AnalysisResultStorage(new StubObjectWriter(), properties);
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(102L);
        job.setSourceFileId(202L);
        job.setSourceBucket("source-bucket");
        job.setSourceKey("uploads/diagram.png");
        job.prePersist();

        ObjectReference reference = storage.resolveResultObjectReference(job);
        ObjectWriteResult writeResult = storage.storeTerraformDraft(reference, new AnalysisResult(
                "stub",
                "provider \"aws\" {}",
                "explanation",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(writeResult.bucket()).isEqualTo("source-bucket");
        assertThat(writeResult.key()).startsWith("analysis-results/102/");
    }

    @Test
    void recordedIntentFreezesIdentityAcrossConfigurationChanges() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setResultBucketName("new-bucket");
        properties.setResultKeyPrefix("new-prefix");
        AnalysisResultStorage storage = new AnalysisResultStorage(new StubObjectWriter(), properties);
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(103L); job.setSourceFileId(203L); job.setSourceBucket("source");
        job.setSourceKey("source.png"); job.prePersist();
        job.setResultObjectIntentBucket("original-bucket");
        job.setResultObjectIntentKey("original-prefix/103/job/main.tf");

        assertThat(storage.resolveResultObjectReference(job))
                .isEqualTo(new ObjectReference("original-bucket", "original-prefix/103/job/main.tf"));
    }
}
