package com.terraformers.modernization.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.projectcore.ProjectDomainService;
import com.terraformers.modernization.storage.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Private, finite-retention evidence, independent of successful result/file registration. */
@Service
public class AnalysisDiagnosticStorage {
    static final Duration RETENTION = Duration.ofDays(7);
    private static final Logger log = LoggerFactory.getLogger(AnalysisDiagnosticStorage.class);
    private final AnalysisJobRepository jobs;
    private final ObjectWriter writer;
    private final ObjectReader reader;
    private final ObjectMapper mapper;
    private final AnalysisRuntimeProperties properties;
    private final ProjectDomainService projects;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final Set<String> submitted = ConcurrentHashMap.newKeySet();

    public AnalysisDiagnosticStorage(AnalysisJobRepository jobs, ObjectWriter writer, ObjectReader reader,
            ObjectMapper mapper, AnalysisRuntimeProperties properties, ProjectDomainService projects,
            Clock clock, PlatformTransactionManager manager) {
        this.jobs = jobs; this.writer = writer; this.reader = reader; this.mapper = mapper;
        this.properties = properties; this.projects = projects; this.clock = clock;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void begin(AnalysisJobEntity claim) {
        try {
            transactions.executeWithoutResult(tx -> jobs.lockDiagnosticGeneration(claim.getId(), claim.getClaimGeneration())
                    .filter(job -> job.getStatus() == AnalysisJobStatus.RUNNING && job.getDiagnosticKey() == null
                            && job.getLeaseExpiresAt() != null && job.getLeaseExpiresAt().isAfter(clock.instant()))
                    .ifPresent(job -> {
                        String bucket = properties.getResultBucketName();
                        job.setDiagnosticBucket(bucket == null || bucket.isBlank() ? job.getSourceBucket() : bucket);
                        job.setDiagnosticKey("analysis-diagnostics/" + job.getProjectId() + "/" + job.getId()
                                + "/" + job.getClaimGeneration() + ".json");
                        job.setDiagnosticStatus("PENDING");
                        job.setDiagnosticExpiresAt(clock.instant().plus(RETENTION));
                        jobs.saveAndFlush(job);
                    }));
        } catch (RuntimeException exception) { safeStorageFailure(exception); }
    }

    public void finish(AnalysisJobEntity claim, AnalysisDiagnosticEvidence evidence) {
        boolean writeAttempted = false;
        try {
            AnalysisJobEntity job = transactions.execute(tx -> jobs.findById(claim.getId()).orElseThrow());
            if (job.getClaimGeneration() != claim.getClaimGeneration() || job.getDiagnosticKey() == null
                    || !"PENDING".equals(job.getDiagnosticStatus())) return;
            var bundle = evidence.bundle(mapper);
            byte[] bytes = mapper.writeValueAsBytes(bundle);
            if (bytes.length > AnalysisDiagnosticEvidence.MAX_BUNDLE_BYTES) {
                incomplete(claim); return; // Never silently truncate and claim completeness.
            }
            ObjectReference reference = reference(job);
            writeAttempted = true;
            var written = writer.writeText(new ObjectWriteRequest(reference.bucket(), reference.key(),
                    new String(bytes, StandardCharsets.UTF_8), "application/json"));
            if (!written.persisted() || !reference.bucket().equals(written.bucket())
                    || !reference.key().equals(written.key()) || !reader.isAvailable()) {
                incomplete(claim); return;
            }
            String hash = AnalysisDiagnosticEvidence.sha(bytes);
            byte[] readback = reader.readContent(reference, AnalysisDiagnosticEvidence.MAX_BUNDLE_BYTES).bytes();
            if (!hash.equals(AnalysisDiagnosticEvidence.sha(readback))) { incomplete(claim); return; }
            Boolean committed = transactions.execute(tx -> jobs.lockDiagnosticGeneration(claim.getId(), claim.getClaimGeneration())
                    .filter(current -> "PENDING".equals(current.getDiagnosticStatus())
                            && reference.key().equals(current.getDiagnosticKey())
                            && current.getDiagnosticExpiresAt().isAfter(clock.instant()))
                    .map(current -> {
                        current.setDiagnosticSha256(hash);
                        current.setDiagnosticStatus(Boolean.TRUE.equals(bundle.get("complete")) ? "AVAILABLE" : "INCOMPLETE");
                        jobs.saveAndFlush(current); return true;
                    }).orElse(false));
            if (!Boolean.TRUE.equals(committed)) {
                // A sweep may have removed the intent's object while a late writer was blocked.
                // Re-arm accountability before compensating that late write; never restore availability.
                transactions.executeWithoutResult(tx -> jobs.lockDiagnosticGeneration(claim.getId(), claim.getClaimGeneration())
                        .ifPresent(current -> { current.setDiagnosticStatus("INCOMPLETE"); jobs.saveAndFlush(current); }));
                expire(claim.getId(), claim.getClaimGeneration());
            }
        } catch (Exception exception) { incomplete(claim); safeStorageFailure(exception); }
        finally {
            if (writeAttempted) {
                try {
                    Boolean expired = transactions.execute(tx -> jobs.lockDiagnosticGeneration(claim.getId(), claim.getClaimGeneration())
                            .filter(job -> job.getDiagnosticExpiresAt() != null && !job.getDiagnosticExpiresAt().isAfter(clock.instant()))
                            .map(job -> { job.setDiagnosticStatus("INCOMPLETE"); jobs.saveAndFlush(job); return true; }).orElse(false));
                    if (Boolean.TRUE.equals(expired)) expire(claim.getId(), claim.getClaimGeneration());
                } catch (RuntimeException exception) { safeStorageFailure(exception); }
            }
        }
    }

    private void incomplete(AnalysisJobEntity claim) {
        try {
            transactions.executeWithoutResult(tx -> jobs.lockDiagnosticGeneration(claim.getId(), claim.getClaimGeneration())
                    .filter(job -> !"EXPIRED".equals(job.getDiagnosticStatus()))
                    .ifPresent(job -> { job.setDiagnosticStatus("INCOMPLETE"); jobs.saveAndFlush(job); }));
        } catch (RuntimeException exception) { safeStorageFailure(exception); }
    }

    /** Authorization applies even to public projects. The normal accessible-project rule is insufficient. */
    public Map<String, Object> read(String id, UserEntity requester, boolean originals) {
        AnalysisJobEntity job = jobs.findById(id).orElseThrow(() -> new NoSuchElementException("analysis job not found"));
        projects.requireModifiableProject(job.getProjectId(), requester); // Existing owner/admin policy; no new grants.
        if ((!"AVAILABLE".equals(job.getDiagnosticStatus()) && !"INCOMPLETE".equals(job.getDiagnosticStatus()))
                || job.getDiagnosticSha256() == null || job.getDiagnosticExpiresAt() == null
                || !job.getDiagnosticExpiresAt().isAfter(clock.instant())) return unavailable(job);
        try {
            byte[] bytes = reader.readContent(reference(job), AnalysisDiagnosticEvidence.MAX_BUNDLE_BYTES).bytes();
            if (!job.getDiagnosticSha256().equals(AnalysisDiagnosticEvidence.sha(bytes))) return unavailable(job);
            Map<String, Object> bundle = mapper.readValue(bytes, new TypeReference<>() {});
            if (!job.getId().equals(bundle.get("jobId")) || !job.getProjectId().toString().equals(String.valueOf(bundle.get("projectId")))
                    || job.getClaimGeneration() != ((Number) bundle.get("claimGeneration")).longValue()
                    ) return unavailable(job);
            var response = new LinkedHashMap<>(originals ? bundle : AnalysisDiagnosticEvidence.summary(bundle));
            response.put("storageStatus", job.getDiagnosticStatus());
            response.put("status", Boolean.TRUE.equals(bundle.get("complete")) && "AVAILABLE".equals(job.getDiagnosticStatus())
                    ? "AVAILABLE" : "DIAGNOSTIC_EVIDENCE_INCOMPLETE"); response.put("evidenceSha256", job.getDiagnosticSha256());
            response.put("expiresAt", job.getDiagnosticExpiresAt().toString());
            return response;
        } catch (Exception exception) { safeStorageFailure(exception); return unavailable(job); }
    }

    private Map<String, Object> unavailable(AnalysisJobEntity job) {
        return Map.of("jobId", job.getId(), "projectId", job.getProjectId(),
                "status", "DIAGNOSTIC_EVIDENCE_INCOMPLETE", "complete", false,
                "storageStatus", job.getDiagnosticStatus() == null ? "NOT_CAPTURED" : job.getDiagnosticStatus());
    }

    public void dispatchExpired(Executor executor, int batchSize) {
        for (AnalysisJobEntity job : jobs.findExpiredDiagnostics(clock.instant(), PageRequest.of(0, batchSize))) {
            if (!submitted.add(job.getId())) continue;
            try { executor.execute(() -> {
                try { expire(job.getId(), job.getClaimGeneration()); }
                catch (RuntimeException exception) { safeStorageFailure(exception); }
                finally { submitted.remove(job.getId()); }
            }); } catch (RuntimeException exception) { submitted.remove(job.getId()); safeStorageFailure(exception); }
        }
    }

    void expire(String id, long generation) {
        AnalysisJobEntity job = transactions.execute(tx -> jobs.findById(id).orElseThrow());
        if (job.getClaimGeneration() != generation || job.getDiagnosticExpiresAt() == null
                || job.getDiagnosticExpiresAt().isAfter(clock.instant())) return;
        if (!(writer instanceof ObjectRemover remover)) throw new IllegalStateException("diagnostic cleanup unavailable");
        remover.remove(reference(job)); // Idempotent; outside job row-lock transaction.
        transactions.executeWithoutResult(tx -> jobs.lockDiagnosticGeneration(id, generation).ifPresent(current -> {
            if (!current.getDiagnosticExpiresAt().isAfter(clock.instant())) {
                current.setDiagnosticStatus("EXPIRED"); current.setDiagnosticSha256(null); jobs.saveAndFlush(current);
            }
        }));
    }

    private ObjectReference reference(AnalysisJobEntity job) {
        return new ObjectReference(job.getDiagnosticBucket(), job.getDiagnosticKey());
    }
    private void safeStorageFailure(Exception exception) {
        log.warn("Analysis diagnostic evidence unavailable errorClass={}", exception.getClass().getSimpleName());
    }
}
