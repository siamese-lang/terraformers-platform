package com.terraformers.modernization.analysis;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
