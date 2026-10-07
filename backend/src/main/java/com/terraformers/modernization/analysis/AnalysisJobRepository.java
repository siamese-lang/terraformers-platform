package com.terraformers.modernization.analysis;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJobEntity, String> {

    Optional<AnalysisJobEntity> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    @Query("""
            select job.id from AnalysisJobEntity job
             where job.attemptCount = 0 and job.createdAt > :acceptedAfter
               and ((job.status = :pending and (job.nextAttemptAt is null or job.nextAttemptAt <= :now))
                or (job.status = :running and (job.leaseExpiresAt is null or job.leaseExpiresAt <= :now)))
             order by job.createdAt asc, job.id asc
            """)
    List<String> findEligibleJobIdsBeforeCutoff(AnalysisJobStatus pending, AnalysisJobStatus running, Instant now,
            Instant acceptedAfter, Pageable pageable);

    default List<String> findEligibleJobIds(AnalysisJobStatus pending, AnalysisJobStatus running, Instant now,
            Pageable pageable) {
        return findEligibleJobIdsBeforeCutoff(pending, running, now,
                now.minus(AnalysisRuntimeProperties.ACCEPTED_AGE_CUTOFF), pageable);
    }

    @Query("""
            select job.id from AnalysisJobEntity job
             where job.status = :failed and job.resultCleanupStatus = :pendingCleanup
               and job.resultObjectIntentBucket is not null and job.resultObjectIntentKey is not null
             order by job.updatedAt asc, job.id asc
            """)
    List<String> findPendingCleanupJobIds(AnalysisJobStatus failed,
            AnalysisResultCleanupStatus pendingCleanup, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.status = :running,
                   job.updatedAt = :claimedAt
             where job.id = :jobId
               and job.status = :pending
            """)
    int claimPending(
            @Param("jobId") String jobId,
            @Param("pending") AnalysisJobStatus pending,
            @Param("running") AnalysisJobStatus running,
            @Param("claimedAt") Instant claimedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.status = :running,
                   job.attemptCount = job.attemptCount + 1,
                   job.claimGeneration = job.claimGeneration + 1,
                   job.leaseExpiresAt = :leaseExpiresAt,
                   job.nextAttemptAt = null,
                   job.updatedAt = :now
             where job.id = :jobId
               and :leaseExpiresAt > :now
               and job.attemptCount = 0 and job.createdAt > :acceptedAfter
               and ((job.status = :pending and (job.nextAttemptAt is null or job.nextAttemptAt <= :now))
                    or (job.status = :running and (job.leaseExpiresAt is null or job.leaseExpiresAt <= :now)))
            """)
    int claimEligibleBeforeCutoff(String jobId, AnalysisJobStatus pending, AnalysisJobStatus running,
            Instant now, Instant leaseExpiresAt, Instant acceptedAfter);

    default int claimEligible(String jobId, AnalysisJobStatus pending, AnalysisJobStatus running,
            Instant now, Instant leaseExpiresAt) {
        return claimEligibleBeforeCutoff(jobId, pending, running, now, leaseExpiresAt,
                now.minus(AnalysisRuntimeProperties.ACCEPTED_AGE_CUTOFF));
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job set job.leaseExpiresAt = :newLeaseExpiry, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
               and :newLeaseExpiry > job.leaseExpiresAt and job.createdAt > :acceptedAfter
            """)
    int renewLeaseBeforeCutoff(String jobId, AnalysisJobStatus running, long generation, Instant now,
            Instant newLeaseExpiry, Instant acceptedAfter);

    default int renewLease(String jobId, AnalysisJobStatus running, long generation, Instant now, Instant newLeaseExpiry) {
        return renewLeaseBeforeCutoff(jobId, running, generation, now, newLeaseExpiry,
                now.minus(AnalysisRuntimeProperties.ACCEPTED_AGE_CUTOFF));
    }

    /*
     * This pessimistic ownership lock is transaction-local. It must be acquired and consumed
     * inside the same transaction as the protected mutation; its result must never be exposed
     * as a reusable authorization or fencing boolean.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job from AnalysisJobEntity job
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
            """)
    Optional<AnalysisJobEntity> lockOwned(String jobId, AnalysisJobStatus running, long generation, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.status = :pending, job.nextAttemptAt = :nextAttemptAt,
                   job.leaseExpiresAt = null, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
               and :nextAttemptAt > :now and job.attemptCount = 0
            """)
    int scheduleRetryOwned(String jobId, AnalysisJobStatus running, AnalysisJobStatus pending,
            long generation, Instant now, Instant nextAttemptAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.status = :failed, job.failureReason = :failureReason,
                   job.qualityContractVersion = :qualityContractVersion,
                   job.technicalStatus = :technicalStatus,
                   job.knowledgeStatus = :knowledgeStatus,
                   job.qualityStatus = :qualityStatus,
                   job.projectDecisionStatus = :projectDecisionStatus,
                   job.runtimeQualityBoundary = :runtimeQualityBoundary,
                   job.qualityReasons = :qualityReasons,
                   job.leaseExpiresAt = null, job.nextAttemptAt = null,
                   job.terminalAt = :now, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
            """)
    int markFailedOwned(String jobId, AnalysisJobStatus running, AnalysisJobStatus failed,
            long generation, Instant now, String failureReason, String qualityContractVersion,
            EvidenceQualityAssessment.TechnicalStatus technicalStatus,
            EvidenceQualityAssessment.KnowledgeStatus knowledgeStatus,
            EvidenceQualityAssessment.QualityStatus qualityStatus,
            EvidenceQualityAssessment.ProjectDecisionStatus projectDecisionStatus,
            EvidenceQualityAssessment.RuntimeQualityBoundary runtimeQualityBoundary, String qualityReasons);

    default int markFailedOwned(String jobId, AnalysisJobStatus running, AnalysisJobStatus failed,
            long generation, Instant now, String failureReason) {
        EvidenceQualityAssessment quality = TerminalQualityAssessmentMapper.failure(new IllegalStateException());
        return markFailedOwned(jobId, running, failed, generation, now, failureReason,
                quality.contractVersion(), quality.technicalStatus(), quality.knowledgeStatus(),
                quality.qualityStatus(), quality.projectDecisionStatus(), quality.runtimeQualityBoundary(), "");
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.resultObjectIntentBucket = :bucket, job.resultObjectIntentKey = :key,
                   job.resultCleanupStatus = :pendingCleanup, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
               and ((job.resultObjectIntentBucket is null and job.resultObjectIntentKey is null)
                    or (job.resultObjectIntentBucket = :bucket and job.resultObjectIntentKey = :key))
               and job.createdAt > :acceptedAfter
            """)
    int recordResultObjectIntentBeforeCutoff(String jobId, AnalysisJobStatus running, long generation, Instant now,
            String bucket, String key, AnalysisResultCleanupStatus pendingCleanup, Instant acceptedAfter);

    default int recordResultObjectIntentOwned(String jobId, AnalysisJobStatus running, long generation, Instant now,
            String bucket, String key, AnalysisResultCleanupStatus pendingCleanup) {
        return recordResultObjectIntentBeforeCutoff(jobId, running, generation, now, bucket, key, pendingCleanup,
                now.minus(AnalysisRuntimeProperties.ACCEPTED_AGE_CUTOFF));
    }

    // Re-arm AFTER a late write, even if earlier failed-job cleanup already deleted the key.
    // Existing cleanup's row lock serializes its completion with this re-arm.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job set job.resultCleanupStatus = :pendingCleanup, job.updatedAt = :now
             where job.id = :jobId and job.claimGeneration = :generation
               and job.status in (:running, :failed)
               and job.resultObjectIntentBucket = :bucket and job.resultObjectIntentKey = :key
            """)
    int rearmResultCleanup(String jobId, long generation, String bucket, String key,
            AnalysisJobStatus running, AnalysisJobStatus failed, AnalysisResultCleanupStatus pendingCleanup, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.status = :failed, job.failureReason = :failureReason,
                   job.qualityContractVersion = :qualityContractVersion, job.technicalStatus = :technicalStatus,
                   job.knowledgeStatus = :knowledgeStatus, job.qualityStatus = :qualityStatus,
                   job.projectDecisionStatus = :projectDecisionStatus, job.runtimeQualityBoundary = :runtimeQualityBoundary,
                   job.qualityReasons = :qualityReasons, job.terminalAt = :now, job.updatedAt = :now,
                   job.leaseExpiresAt = null, job.nextAttemptAt = null
             where job.status in (:pending, :running)
               and ((:ageExpired = true and job.createdAt <= :acceptedAfter)
                 or (:ageExpired = false and job.attemptCount >= 1
                     and (job.status = :pending or job.leaseExpiresAt is null or job.leaseExpiresAt <= :now)))
            """)
    int terminalizeIneligible(AnalysisJobStatus pending, AnalysisJobStatus running, AnalysisJobStatus failed,
            Instant now, Instant acceptedAfter, boolean ageExpired, String failureReason, String qualityContractVersion,
            EvidenceQualityAssessment.TechnicalStatus technicalStatus,
            EvidenceQualityAssessment.KnowledgeStatus knowledgeStatus,
            EvidenceQualityAssessment.QualityStatus qualityStatus,
            EvidenceQualityAssessment.ProjectDecisionStatus projectDecisionStatus,
            EvidenceQualityAssessment.RuntimeQualityBoundary runtimeQualityBoundary, String qualityReasons);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.resultCleanupStatus = :completed, job.updatedAt = :now
             where job.id = :jobId and job.claimGeneration = :generation
               and job.resultCleanupStatus = :pendingCleanup
               and job.resultObjectIntentBucket = :bucket and job.resultObjectIntentKey = :key
            """)
    int markResultCleanupCompleted(String jobId, long generation, String bucket, String key,
            AnalysisResultCleanupStatus pendingCleanup, AnalysisResultCleanupStatus completed, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job set job.updatedAt = :now
             where job.id = :jobId and job.status = :failed
               and job.resultCleanupStatus = :pendingCleanup
               and job.resultObjectIntentBucket = :bucket and job.resultObjectIntentKey = :key
            """)
    int touchPendingCleanup(String jobId, AnalysisJobStatus failed,
            AnalysisResultCleanupStatus pendingCleanup, String bucket, String key, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job from AnalysisJobEntity job
             where job.id = :jobId and job.status = :failed
               and job.resultCleanupStatus = :pendingCleanup
               and job.resultObjectIntentBucket is not null and job.resultObjectIntentKey is not null
            """)
    Optional<AnalysisJobEntity> lockPendingCleanup(String jobId, AnalysisJobStatus failed,
            AnalysisResultCleanupStatus pendingCleanup);
}
