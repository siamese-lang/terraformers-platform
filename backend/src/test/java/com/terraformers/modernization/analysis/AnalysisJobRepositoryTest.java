package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.EntityManager;
import java.util.List;

@DataJpaTest
@ActiveProfiles("test")
class AnalysisJobRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final Instant LEASE = Instant.parse("2026-09-28T00:05:00Z");

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void ownedTerminalTimeSurvivesCleanupAndCannotBeReplacedByAStaleWorker() {
        String id = savePending("terminal-time").getId();
        entityManager.clear();
        Instant accepted = repository.findById(id).orElseThrow().getCreatedAt();
        Instant terminal = accepted.plusSeconds(10);
        repository.claimEligible(id, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                accepted, accepted.plusSeconds(60));
        repository.renewLease(id, AnalysisJobStatus.RUNNING, 1,
                accepted.plusSeconds(1), accepted.plusSeconds(90));
        repository.recordResultObjectIntentOwned(id, AnalysisJobStatus.RUNNING, 1,
                accepted.plusSeconds(2), "bucket", "draft.tf", AnalysisResultCleanupStatus.PENDING);
        assertThat(repository.findById(id).orElseThrow().getTerminalAt()).isNull();
        assertThat(repository.markFailedOwned(id, AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED,
                99, terminal, "stale")).isZero();
        assertThat(repository.findById(id).orElseThrow().getTerminalAt()).isNull();
        assertThat(repository.markFailedOwned(id, AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED,
                1, terminal, "owned")).isEqualTo(1);
        repository.markResultCleanupCompleted(id, 1, "bucket", "draft.tf", AnalysisResultCleanupStatus.PENDING,
                AnalysisResultCleanupStatus.COMPLETED, terminal.plusSeconds(90));
        entityManager.clear();
        AnalysisJobEntity restored = repository.findById(id).orElseThrow();
        assertThat(restored.getTerminalAt()).isEqualTo(terminal);
        assertThat(restored.getUpdatedAt()).isAfter(terminal);
        assertThat(AnalysisJobResponse.from(restored).timing().acceptedToTerminalMs()).isEqualTo(10000L);
        assertThat(repository.markFailedOwned(id, AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED,
                1, terminal.plusSeconds(120), "late")).isZero();
        assertThat(repository.findById(id).orElseThrow().getTerminalAt()).isEqualTo(terminal);
    }

    @Test
    void historicalTerminalRowStaysWithoutAnInferredTerminalTimeAfterUpdateAndReload() {
        AnalysisJobEntity entity = savePending("legacy-timing");
        entity.setStatus(AnalysisJobStatus.SUCCEEDED);
        repository.saveAndFlush(entity);
        entity.setAnalysisWarnings("edited later");
        repository.saveAndFlush(entity);
        entityManager.clear();
        AnalysisJobEntity restored = repository.findById(entity.getId()).orElseThrow();
        assertThat(restored.getTerminalAt()).isNull();
        assertThat(AnalysisJobResponse.from(restored).timing().acceptedToTerminalMs()).isNull();
    }

    @Test
    void semanticOriginReasonIsPersistedWithoutTurningTechnicalPassIntoFailure() {
        AnalysisJobEntity entity = savePending("origin-authorization-reload");
        var quality = new EvidenceQualityAssessment(EvidenceQualityAssessment.CONTRACT_VERSION,
                EvidenceQualityAssessment.TechnicalStatus.PASS,
                EvidenceQualityAssessment.KnowledgeStatus.COMPLETE,
                EvidenceQualityAssessment.QualityStatus.DEGRADED,
                EvidenceQualityAssessment.ProjectDecisionStatus.UNKNOWN,
                EvidenceQualityAssessment.RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                List.of(EvidenceQualityAssessment.Reason.CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        entity.setQualityAssessment(quality);
        repository.saveAndFlush(entity);
        entityManager.clear();
        var restored = repository.findById(entity.getId()).orElseThrow();
        var response = AnalysisJobResponse.from(restored);
        assertThat(restored.getTechnicalStatus()).isEqualTo(EvidenceQualityAssessment.TechnicalStatus.PASS);
        assertThat(restored.getQualityStatus()).isEqualTo(EvidenceQualityAssessment.QualityStatus.DEGRADED);
        assertThat(restored.qualityReasonValues()).containsExactly(
                EvidenceQualityAssessment.Reason.CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING);
        assertThat(response.quality().reasons()).containsExactlyElementsOf(quality.reasons());
    }

    @Test
    void qualitySnapshotSurvivesFreshPersistenceContextWithoutRecomputation() {
        AnalysisJobEntity entity = savePending("quality-reload");
        var quality = new EvidenceQualityAssessment(EvidenceQualityAssessment.CONTRACT_VERSION,
                EvidenceQualityAssessment.TechnicalStatus.PASS,
                EvidenceQualityAssessment.KnowledgeStatus.INCOMPLETE,
                EvidenceQualityAssessment.QualityStatus.DEGRADED,
                EvidenceQualityAssessment.ProjectDecisionStatus.UNKNOWN,
                EvidenceQualityAssessment.RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                List.of(EvidenceQualityAssessment.Reason.OFFICIAL_KNOWLEDGE_NOT_AVAILABLE),
                List.of("aws_alb"), List.of("aws_alb"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        entity.setQualityAssessment(quality);
        repository.saveAndFlush(entity);
        entityManager.clear();

        AnalysisJobEntity reloaded = repository.findById(entity.getId()).orElseThrow();
        assertThat(reloaded.getQualityContractVersion()).isEqualTo(quality.contractVersion());
        assertThat(reloaded.getTechnicalStatus()).isEqualTo(quality.technicalStatus());
        assertThat(reloaded.getKnowledgeStatus()).isEqualTo(quality.knowledgeStatus());
        assertThat(reloaded.getQualityStatus()).isEqualTo(quality.qualityStatus());
        assertThat(reloaded.getProjectDecisionStatus()).isEqualTo(quality.projectDecisionStatus());
        assertThat(reloaded.getRuntimeQualityBoundary()).isEqualTo(quality.runtimeQualityBoundary());
        assertThat(reloaded.qualityReasonValues()).containsExactlyElementsOf(quality.reasons());
    }

    @Test
    void pendingJobCanBeClaimedExactlyOnce() {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(102L);
        entity.setSourceFileId(202L);
        entity.setSourceBucket("claim-bucket");
        entity.setSourceKey("uploads/claim.png");
        entity.setCorrelationId("claim-once");
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        entity.setStatus(AnalysisJobStatus.PENDING);
        String jobId = repository.saveAndFlush(entity).getId();

        int first = repository.claimPending(
                jobId,
                AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING,
                Instant.now()
        );
        int second = repository.claimPending(
                jobId,
                AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING,
                Instant.now()
        );

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(repository.findById(jobId))
                .get()
                .extracting(AnalysisJobEntity::getStatus)
                .isEqualTo(AnalysisJobStatus.RUNNING);
    }

    @Test
    void savesAnalysisJobLifecycleStateWithNumericProjectAndFileIds() {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(101L);
        entity.setSourceFileId(201L);
        entity.setSourceBucket("example-bucket");
        entity.setSourceKey("uploads/diagram.png");
        entity.setCorrelationId("corr-1");
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        entity.setStatus(AnalysisJobStatus.PENDING);

        AnalysisJobEntity saved = repository.saveAndFlush(entity);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getProjectId()).isEqualTo(101L);
        assertThat(saved.getSourceFileId()).isEqualTo(201L);
        assertThat(saved.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        assertThat(saved.getAnalysisMode()).isEqualTo(AnalysisMode.INTEGRATED_JAVA);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(saved.getClaimGeneration()).isZero();
        assertThat(saved.getNextAttemptAt()).isNull();
        assertThat(saved.getLeaseExpiresAt()).isNull();
        assertThat(saved.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.NOT_REQUIRED);
    }

    @Test
    void durableClaimHonorsEligibilityAndReclaimsOnlyExpiredLeases() {
        String jobId = savePending("durable-claim").getId();

        assertThat(repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, NOW.minusSeconds(1))).isZero();
        assertThat(repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, NOW)).isZero();
        AnalysisJobEntity unclaimed = repository.findById(jobId).orElseThrow();
        assertThat(unclaimed.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        assertThat(unclaimed.getAttemptCount()).isZero();
        assertThat(unclaimed.getClaimGeneration()).isZero();
        assertThat(unclaimed.getLeaseExpiresAt()).isNull();

        assertThat(repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, LEASE)).isEqualTo(1);
        AnalysisJobEntity firstClaim = repository.findById(jobId).orElseThrow();
        assertThat(firstClaim.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(firstClaim.getAttemptCount()).isEqualTo(1);
        assertThat(firstClaim.getClaimGeneration()).isEqualTo(1);
        assertThat(firstClaim.getLeaseExpiresAt()).isEqualTo(LEASE);

        assertThat(repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW.plusSeconds(1), LEASE.plusSeconds(1))).isZero();
        assertThat(repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                LEASE, LEASE.plusSeconds(300))).isEqualTo(1);
        AnalysisJobEntity reclaimed = repository.findById(jobId).orElseThrow();
        assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
        assertThat(reclaimed.getClaimGeneration()).isEqualTo(2);
        assertThat(reclaimed.getLeaseExpiresAt()).isEqualTo(LEASE.plusSeconds(300));
    }

    @Test
    void boundedDiscoveryIncludesDuePendingExpiredAndLegacyRunningOnly() {
        AnalysisJobEntity due = savePending("due");
        AnalysisJobEntity future = savePending("future");
        future.setNextAttemptAt(NOW.plusSeconds(1));
        repository.saveAndFlush(future);
        AnalysisJobEntity expired = savePending("expired");
        repository.claimEligible(expired.getId(), AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW.minusSeconds(120), NOW.minusSeconds(60));
        AnalysisJobEntity active = savePending("active");
        repository.claimEligible(active.getId(), AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW.minusSeconds(1), NOW.plusSeconds(60));
        AnalysisJobEntity legacy = savePending("legacy-null-lease");
        repository.claimPending(legacy.getId(), AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW.minusSeconds(60));
        AnalysisJobEntity terminal = savePending("terminal");
        terminal.setStatus(AnalysisJobStatus.SUCCEEDED);
        repository.saveAndFlush(terminal);

        var discovered = repository.findEligibleJobIds(AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, PageRequest.of(0, 4));

        assertThat(discovered).containsExactlyInAnyOrder(due.getId(), expired.getId(), legacy.getId());
        assertThat(discovered).doesNotContain(future.getId(), active.getId(), terminal.getId());
        assertThat(repository.claimEligible(legacy.getId(), AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, LEASE)).isEqualTo(1);
        AnalysisJobEntity reclaimedLegacy = repository.findById(legacy.getId()).orElseThrow();
        assertThat(reclaimedLegacy.getAttemptCount()).isEqualTo(1);
        assertThat(reclaimedLegacy.getClaimGeneration()).isEqualTo(1);
        assertThat(reclaimedLegacy.getLeaseExpiresAt()).isEqualTo(LEASE);
    }

    @Test
    void futurePendingJobIsNotEligibleAndIsNotMutated() {
        AnalysisJobEntity job = savePending("future-pending");
        job.setNextAttemptAt(NOW.plusSeconds(60));
        repository.saveAndFlush(job);

        assertThat(repository.claimEligible(job.getId(), AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                NOW, LEASE)).isZero();
        AnalysisJobEntity unchanged = repository.findById(job.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        assertThat(unchanged.getAttemptCount()).isZero();
        assertThat(unchanged.getClaimGeneration()).isZero();
        assertThat(unchanged.getLeaseExpiresAt()).isNull();
    }

    @Test
    void staleGenerationIsFencedAfterExpiredLeaseReclaim() {
        String jobId = savePending("stale-fence").getId();
        repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, NOW, LEASE);
        repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING,
                LEASE, LEASE.plusSeconds(300));

        assertThat(repository.lockOwned(jobId, AnalysisJobStatus.RUNNING, 1, LEASE.plusSeconds(1))).isEmpty();
        assertThat(repository.recordResultObjectIntentOwned(jobId, AnalysisJobStatus.RUNNING, 1,
                LEASE.plusSeconds(1), "old-bucket", "old-key", AnalysisResultCleanupStatus.PENDING)).isZero();
        assertThat(repository.scheduleRetryOwned(jobId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.PENDING,
                1, LEASE.plusSeconds(1), LEASE.plusSeconds(60))).isZero();
        assertThat(repository.lockOwned(jobId, AnalysisJobStatus.RUNNING, 2, LEASE.plusSeconds(1))).isPresent();
        assertThat(repository.findById(jobId).orElseThrow().getClaimGeneration()).isEqualTo(2);
    }

    @Test
    void ownershipTransitionsRequireCurrentUnexpiredGeneration() {
        String renewalId = savePending("renewal").getId();
        repository.claimEligible(renewalId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, NOW, LEASE);
        assertThat(repository.renewLease(renewalId, AnalysisJobStatus.RUNNING, 99, NOW, LEASE.plusSeconds(60))).isZero();
        assertThat(repository.renewLease(renewalId, AnalysisJobStatus.RUNNING, 1,
                NOW, LEASE.minusSeconds(60))).isZero();
        assertThat(repository.renewLease(renewalId, AnalysisJobStatus.RUNNING, 1, NOW, LEASE)).isZero();
        assertThat(repository.renewLease(renewalId, AnalysisJobStatus.RUNNING, 1, NOW, LEASE.plusSeconds(60))).isEqualTo(1);
        assertThat(repository.renewLease(renewalId, AnalysisJobStatus.RUNNING, 1,
                LEASE.plusSeconds(61), LEASE.plusSeconds(120))).isZero();

        String retryId = savePending("retry").getId();
        repository.claimEligible(retryId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, NOW, LEASE);
        Instant retryAt = NOW.plusSeconds(60);
        assertThat(repository.scheduleRetryOwned(retryId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.PENDING,
                1, NOW, NOW.minusSeconds(1))).isZero();
        assertThat(repository.scheduleRetryOwned(retryId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.PENDING,
                1, NOW, NOW)).isZero();
        AnalysisJobEntity beforeRetry = repository.findById(retryId).orElseThrow();
        assertThat(beforeRetry.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(beforeRetry.getLeaseExpiresAt()).isEqualTo(LEASE);
        assertThat(beforeRetry.getAttemptCount()).isEqualTo(1);
        assertThat(beforeRetry.getClaimGeneration()).isEqualTo(1);
        assertThat(beforeRetry.getNextAttemptAt()).isNull();
        assertThat(repository.scheduleRetryOwned(retryId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.PENDING,
                1, NOW, retryAt)).isEqualTo(1);
        AnalysisJobEntity retry = repository.findById(retryId).orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        assertThat(retry.getNextAttemptAt()).isEqualTo(retryAt);
        assertThat(retry.getLeaseExpiresAt()).isNull();
        assertThat(retry.getAttemptCount()).isEqualTo(1);

        String failedId = savePending("failure").getId();
        repository.claimEligible(failedId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, NOW, LEASE);
        assertThat(repository.markFailedOwned(failedId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED,
                2, NOW, "stale")).isZero();
        assertThat(repository.markFailedOwned(failedId, AnalysisJobStatus.RUNNING, AnalysisJobStatus.FAILED,
                1, NOW, "bounded failure")).isEqualTo(1);
        AnalysisJobEntity failed = repository.findById(failedId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("bounded failure");
        assertThat(failed.getLeaseExpiresAt()).isNull();
    }

    @Test
    void objectIntentAndCleanupResolutionMatchTheAccountableObject() {
        String jobId = savePending("object-intent").getId();
        repository.claimEligible(jobId, AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, NOW, LEASE);
        assertThat(repository.recordResultObjectIntentOwned(jobId, AnalysisJobStatus.RUNNING, 2, NOW,
                "bucket", "key", AnalysisResultCleanupStatus.PENDING)).isZero();
        assertThat(repository.recordResultObjectIntentOwned(jobId, AnalysisJobStatus.RUNNING, 1, NOW,
                "bucket", "key", AnalysisResultCleanupStatus.PENDING)).isEqualTo(1);
        assertThat(repository.recordResultObjectIntentOwned(jobId, AnalysisJobStatus.RUNNING, 1, NOW,
                "other-bucket", "other-key", AnalysisResultCleanupStatus.PENDING)).isZero();
        assertThat(repository.markResultCleanupCompleted(jobId, 1, "bucket", "different-key",
                AnalysisResultCleanupStatus.PENDING, AnalysisResultCleanupStatus.COMPLETED, NOW)).isZero();
        assertThat(repository.markResultCleanupCompleted(jobId, 2, "bucket", "key",
                AnalysisResultCleanupStatus.PENDING, AnalysisResultCleanupStatus.COMPLETED, NOW)).isZero();
        assertThat(repository.markResultCleanupCompleted(jobId, 1, "bucket", "key",
                AnalysisResultCleanupStatus.PENDING, AnalysisResultCleanupStatus.COMPLETED, NOW)).isEqualTo(1);
        AnalysisJobEntity resolved = repository.findById(jobId).orElseThrow();
        assertThat(resolved.getResultObjectIntentBucket()).isEqualTo("bucket");
        assertThat(resolved.getResultObjectIntentKey()).isEqualTo("key");
        assertThat(resolved.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.COMPLETED);
    }

    @Test
    void touchingFailedOldestCleanupRotatesLaterPendingWorkIntoBoundedBatch() {
        AnalysisJobEntity first = savePendingCleanup("cleanup-a", "bucket-a", "key-a");
        AnalysisJobEntity second = savePendingCleanup("cleanup-b", "bucket-b", "key-b");
        Instant firstUpdatedAt = repository.findById(first.getId()).orElseThrow().getUpdatedAt();
        Instant secondUpdatedAt = repository.findById(second.getId()).orElseThrow().getUpdatedAt();
        Instant deferredAt = (firstUpdatedAt.isAfter(secondUpdatedAt) ? firstUpdatedAt : secondUpdatedAt)
                .plusSeconds(1)
                .truncatedTo(ChronoUnit.MILLIS);
        String selected = repository.findPendingCleanupJobIds(AnalysisJobStatus.FAILED,
                AnalysisResultCleanupStatus.PENDING, PageRequest.of(0, 1)).get(0);
        AnalysisJobEntity selectedEntity = repository.findById(selected).orElseThrow();
        String selectedBucket = selectedEntity.getResultObjectIntentBucket();
        String selectedKey = selectedEntity.getResultObjectIntentKey();
        String later = selected.equals(first.getId()) ? second.getId() : first.getId();

        assertThat(repository.touchPendingCleanup(selected, AnalysisJobStatus.FAILED,
                AnalysisResultCleanupStatus.PENDING, selectedBucket,
                "different-key", deferredAt)).isZero();
        assertThat(repository.touchPendingCleanup(selected, AnalysisJobStatus.FAILED,
                AnalysisResultCleanupStatus.PENDING, selectedBucket,
                selectedKey, deferredAt)).isEqualTo(1);

        AnalysisJobEntity deferred = repository.findById(selected).orElseThrow();
        assertThat(deferred.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(deferred.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.PENDING);
        assertThat(deferred.getResultObjectIntentBucket()).isEqualTo(selectedBucket);
        assertThat(deferred.getResultObjectIntentKey()).isEqualTo(selectedKey);
        assertThat(deferred.getUpdatedAt()).isEqualTo(deferredAt);
        assertThat(repository.findPendingCleanupJobIds(AnalysisJobStatus.FAILED,
                AnalysisResultCleanupStatus.PENDING, PageRequest.of(0, 1))).containsExactly(later);
    }

    private AnalysisJobEntity savePendingCleanup(String correlationId, String bucket, String key) {
        AnalysisJobEntity entity = savePending(correlationId);
        entity.setStatus(AnalysisJobStatus.FAILED);
        entity.setResultObjectIntentBucket(bucket);
        entity.setResultObjectIntentKey(key);
        entity.setResultCleanupStatus(AnalysisResultCleanupStatus.PENDING);
        return repository.saveAndFlush(entity);
    }

    private AnalysisJobEntity savePending(String correlationId) {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(102L);
        entity.setSourceFileId(202L);
        entity.setSourceBucket("durable-bucket");
        entity.setSourceKey("uploads/durable.png");
        entity.setCorrelationId(correlationId);
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        entity.setStatus(AnalysisJobStatus.PENDING);
        return repository.saveAndFlush(entity);
    }
}
