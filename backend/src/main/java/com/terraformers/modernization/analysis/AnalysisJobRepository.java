package com.terraformers.modernization.analysis;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJobEntity, String> {

    Optional<AnalysisJobEntity> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.status = :failed,
                   job.failureReason = :failureReason,
                   job.updatedAt = :reconciledAt
             where job.status = :pending
                or job.status = :running
            """)
    int failInterrupted(
            @Param("pending") AnalysisJobStatus pending,
            @Param("running") AnalysisJobStatus running,
            @Param("failed") AnalysisJobStatus failed,
            @Param("failureReason") String failureReason,
            @Param("reconciledAt") Instant reconciledAt
    );

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
               and ((job.status = :pending and (job.nextAttemptAt is null or job.nextAttemptAt <= :now))
                    or (job.status = :running and job.leaseExpiresAt is not null and job.leaseExpiresAt <= :now))
            """)
    int claimEligible(String jobId, AnalysisJobStatus pending, AnalysisJobStatus running,
            Instant now, Instant leaseExpiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job set job.leaseExpiresAt = :newLeaseExpiry, job.updatedAt = :now
             where job.id = :jobId and job.status = :running
               and job.claimGeneration = :generation and job.leaseExpiresAt > :now
            """)
    int renewLease(String jobId, AnalysisJobStatus running, long generation, Instant now, Instant newLeaseExpiry);

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
            """)
    int recordResultObjectIntentOwned(String jobId, AnalysisJobStatus running, long generation, Instant now,
            String bucket, String key, AnalysisResultCleanupStatus pendingCleanup);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnalysisJobEntity job
               set job.resultCleanupStatus = :completed, job.updatedAt = :now
             where job.id = :jobId and job.resultCleanupStatus = :pendingCleanup
               and job.resultObjectIntentBucket = :bucket and job.resultObjectIntentKey = :key
            """)
    int markResultCleanupCompleted(String jobId, String bucket, String key,
            AnalysisResultCleanupStatus pendingCleanup, AnalysisResultCleanupStatus completed, Instant now);
}
