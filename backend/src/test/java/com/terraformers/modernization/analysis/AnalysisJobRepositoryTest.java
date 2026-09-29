package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AnalysisJobRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final Instant LEASE = Instant.parse("2026-09-28T00:05:00Z");

    @Autowired
    private AnalysisJobRepository repository;

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
