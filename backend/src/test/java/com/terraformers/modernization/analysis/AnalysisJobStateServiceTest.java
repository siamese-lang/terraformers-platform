package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnalysisJobStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired AnalysisJobRepository repository;
    @Autowired AnalysisJobStateService stateService;
    @MockBean AnalysisJobOrchestrator orchestrator;
    @MockBean AnalysisObservability observability;
    private final List<String> committedJobIds = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedJobs() {
        committedJobIds.forEach(repository::deleteById);
    }

    @Test
    void staleGenerationCannotCreateRelationalMetadataOrSucceedAfterReclaim() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(1L); job.setSourceFileId(2L); job.setSourceBucket("bucket");
        job.setSourceKey("source.png"); job.setStatus(AnalysisJobStatus.PENDING);
        String id = saveCommitted(job);
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(10))).isPresent();
        assertThat(stateService.claimEligible(id, NOW.plusSeconds(10), NOW.plusSeconds(70))).isPresent();

        AnalysisJobEntity reclaimed = repository.findById(id).orElseThrow();
        assertThat(reclaimed.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
        assertThat(reclaimed.getClaimGeneration()).isEqualTo(2);

        boolean finalized = stateService.markSucceededOwned(id, 1, NOW.plusSeconds(11),
                org.mockito.Mockito.mock(AnalysisResult.class),
                new com.terraformers.modernization.storage.ObjectReference("bucket", "key"));

        assertThat(finalized).isFalse();
        verify(orchestrator, never()).storeTerraformDraft(any(), any());
        AnalysisJobEntity current = repository.findById(id).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(current.getAttemptCount()).isEqualTo(2);
        assertThat(current.getClaimGeneration()).isEqualTo(2);
        assertThat(current.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(70));
        assertThat(current.getResultFileId()).isNull();
        assertThat(current.getResultObjectKey()).isNull();
    }

    @Test
    void heartbeatMovesLeaseForwardWithoutChangingAttemptOrGeneration() {
        AnalysisJobEntity job = pendingJob();
        String id = saveCommitted(job);
        Instant firstLease = NOW.plusSeconds(60);
        Instant renewedLease = NOW.plusSeconds(90);
        assertThat(stateService.claimEligible(id, NOW, firstLease)).isPresent();
        AnalysisJobEntity claimed = repository.findById(id).orElseThrow();
        assertThat(claimed.getLeaseExpiresAt()).isEqualTo(firstLease);
        assertThat(claimed.getAttemptCount()).isEqualTo(1);
        assertThat(claimed.getClaimGeneration()).isEqualTo(1);

        assertThat(stateService.renewLease(id, 1, NOW.plusSeconds(20), renewedLease)).isTrue();

        AnalysisJobEntity renewed = repository.findById(id).orElseThrow();
        assertThat(renewed.getLeaseExpiresAt()).isEqualTo(renewedLease);
        assertThat(renewed.getAttemptCount()).isEqualTo(1);
        assertThat(renewed.getClaimGeneration()).isEqualTo(1);
    }

    @Test
    void currentOwnerWritesAndFinalizesTheExactDurableIntent() {
        AnalysisJobEntity job = pendingJob();
        String id = saveCommitted(job);
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(60))).isPresent();
        String key = "analysis-results/1/" + id + "/main.tf";
        assertThat(stateService.recordResultObjectIntentOwned(id, 1, NOW, "bucket", key)).isTrue();
        var reference = new com.terraformers.modernization.storage.ObjectReference("bucket", key);
        var quality = quality(EvidenceQualityAssessment.QualityStatus.DEGRADED,
                EvidenceQualityAssessment.Reason.REQUIRED_EVIDENCE_NOT_RETRIEVED);
        var result = new AnalysisResult("stub", "resource {}", "summary", List.of(), List.of(), List.of(),
                List.of(), quality);
        var writeResult = new com.terraformers.modernization.storage.ObjectWriteResult(
                "metadata-only", false, "bucket", key, null);
        var resultFile = org.mockito.Mockito.mock(
                com.terraformers.modernization.projectcore.ProjectFileEntity.class);
        when(resultFile.getFileId()).thenReturn(44L);
        when(orchestrator.storeTerraformDraft(reference, result)).thenReturn(writeResult);
        when(orchestrator.registerGeneratedTerraform(1L, result, writeResult)).thenReturn(resultFile);
        doAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            entity.setStatus(AnalysisJobStatus.SUCCEEDED);
            entity.setResultFileId(44L);
            entity.setResultObjectKey(key);
            return null;
        }).when(orchestrator).markSucceeded(any(), any(), any(), any());

        assertThat(stateService.markSucceededOwned(id, 1, NOW, result, reference)).isTrue();

        AnalysisJobEntity succeeded = repository.findById(id).orElseThrow();
        assertThat(succeeded.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
        assertThat(succeeded.getResultFileId()).isEqualTo(44L);
        assertThat(succeeded.getResultObjectKey()).isEqualTo(key);
        assertThat(succeeded.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.NOT_REQUIRED);
        assertThat(succeeded.getLeaseExpiresAt()).isNull();
        assertThat(succeeded.getQualityContractVersion()).isEqualTo("evidence-quality-v1");
        assertThat(succeeded.getTechnicalStatus()).isEqualTo(EvidenceQualityAssessment.TechnicalStatus.PASS);
        assertThat(succeeded.getQualityStatus()).isEqualTo(EvidenceQualityAssessment.QualityStatus.DEGRADED);
        assertThat(succeeded.qualityReasonValues())
                .containsExactly(EvidenceQualityAssessment.Reason.REQUIRED_EVIDENCE_NOT_RETRIEVED);
    }

    @Test
    void staleGenerationCannotMutateTerminalQualityAndRetryHasNoSnapshot() {
        String id = saveCommitted(pendingJob());
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(10))).isPresent();
        assertThat(stateService.claimEligible(id, NOW.plusSeconds(10), NOW.plusSeconds(70))).isPresent();
        var blocked = quality(EvidenceQualityAssessment.QualityStatus.UNKNOWN,
                EvidenceQualityAssessment.Reason.PROVIDER_CONTENT_BLOCKED);

        assertThat(stateService.markFailedOwned(id, 1, NOW.plusSeconds(11), "stale", blocked)).isFalse();
        assertThat(repository.findById(id).orElseThrow().getQualityContractVersion()).isNull();
        assertThat(stateService.scheduleRetryOwned(id, 2, NOW.plusSeconds(11), NOW.plusSeconds(20))).isTrue();
        assertThat(repository.findById(id).orElseThrow().getQualityContractVersion()).isNull();
    }

    @Test
    void onlyOwnedFailurePublishesFailedProgress() {
        AnalysisJobEntity job = pendingJob();
        String id = saveCommitted(job);
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(60))).isPresent();

        assertThat(stateService.markFailedOwned(id, 99, NOW.plusSeconds(1), "stale")).isFalse();
        assertThat(repository.findById(id).orElseThrow().getTerminalAt()).isNull();
        verify(orchestrator, never()).publishFailedProgress(any());

        assertThat(stateService.markFailedOwned(id, 1, NOW.plusSeconds(1), "owned")).isTrue();
        verify(orchestrator, times(1)).publishFailedProgress(any());
        AnalysisJobEntity failed = repository.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("owned");
        assertThat(failed.getTerminalAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(failed.getLeaseExpiresAt()).isNull();
        assertThat(failed.getNextAttemptAt()).isNull();
    }

    private AnalysisJobEntity pendingJob() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(1L);
        job.setSourceFileId(2L);
        job.setSourceBucket("bucket");
        job.setSourceKey("source.png");
        job.setStatus(AnalysisJobStatus.PENDING);
        return job;
    }

    private String saveCommitted(AnalysisJobEntity job) {
        String id = repository.saveAndFlush(job).getId();
        committedJobIds.add(id);
        return id;
    }

    private EvidenceQualityAssessment quality(EvidenceQualityAssessment.QualityStatus status,
            EvidenceQualityAssessment.Reason reason) {
        return new EvidenceQualityAssessment(EvidenceQualityAssessment.CONTRACT_VERSION,
                EvidenceQualityAssessment.TechnicalStatus.PASS,
                EvidenceQualityAssessment.KnowledgeStatus.COMPLETE, status,
                EvidenceQualityAssessment.ProjectDecisionStatus.UNKNOWN,
                EvidenceQualityAssessment.RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                List.of(reason), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
