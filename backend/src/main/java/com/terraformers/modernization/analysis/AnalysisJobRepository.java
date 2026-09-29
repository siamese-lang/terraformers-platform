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
             where (job.status = :pending and (job.nextAttemptAt is null or job.nextAttemptAt <= :now))
                or (job.status = :running and (job.leaseExpiresAt is null or job.leaseExpiresAt <= :now))
             order by job.createdAt asc, job.id asc
            """)
    List<String> findEligibleJobIds(AnalysisJobStatus pending, AnalysisJobStatus running, Instant now,
            Pageable pageable);

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
               and ((job.status = :pending and (job.nextAttemptAt is null or job.nextAttemptAt <= :now))
                    or (job.status = :running and (job.leaseExpiresAt is null or job.leaseExpiresAt <= :now)))
            """)
    int claimEligible(String jobId, AnalysisJobStatus pending, AnalysisJobStatus running,
            Instant now, Instant leaseExpiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job set job.leaseExpiresAt = :newLeaseExpiry, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
               and :newLeaseExpiry > job.leaseExpiresAt
            """)
    int renewLease(String jobId, AnalysisJobStatus running, long generation, Instant now, Instant newLeaseExpiry);

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
               and :nextAttemptAt > :now
            """)
    int scheduleRetryOwned(String jobId, AnalysisJobStatus running, AnalysisJobStatus pending,
            long generation, Instant now, Instant nextAttemptAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.status = :failed, job.failureReason = :failureReason,
                   job.leaseExpiresAt = null, job.nextAttemptAt = null, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
            """)
    int markFailedOwned(String jobId, AnalysisJobStatus running, AnalysisJobStatus failed,
            long generation, Instant now, String failureReason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.resultObjectIntentBucket = :bucket, job.resultObjectIntentKey = :key,
                   job.resultCleanupStatus = :pendingCleanup, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
               and ((job.resultObjectIntentBucket is null and job.resultObjectIntentKey is null)
                    or (job.resultObjectIntentBucket = :bucket and job.resultObjectIntentKey = :key))
            """)
    int recordResultObjectIntentOwned(String jobId, AnalysisJobStatus running, long generation, Instant now,
            String bucket, String key, AnalysisResultCleanupStatus pendingCleanup);

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
