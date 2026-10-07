package com.terraformers.modernization.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJobEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "source_file_id", nullable = false)
    private Long sourceFileId;

    @Column(name = "result_file_id")
    private Long resultFileId;

    @Column(name = "source_bucket", nullable = false, length = 255)
    private String sourceBucket;

    @Column(name = "source_key", nullable = false, length = 1024)
    private String sourceKey;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AnalysisJobStatus status = AnalysisJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_mode", nullable = false, length = 32)
    private AnalysisMode analysisMode = AnalysisMode.INTEGRATED_JAVA;

    @Column(length = 64)
    private String provider;

    @Column(name = "result_object_key", length = 1024)
    private String resultObjectKey;

    @Column(name = "result_preview", columnDefinition = "TEXT")
    private String resultPreview;

    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;

    @Column(name = "detected_components", columnDefinition = "TEXT")
    private String detectedComponents;

    @Column(name = "detected_relationships", columnDefinition = "TEXT")
    private String detectedRelationships;

    @Column(name = "analysis_warnings", columnDefinition = "TEXT")
    private String analysisWarnings;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "quality_contract_version", length = 64)
    private String qualityContractVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "technical_status", length = 32)
    private EvidenceQualityAssessment.TechnicalStatus technicalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_status", length = 32)
    private EvidenceQualityAssessment.KnowledgeStatus knowledgeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", length = 32)
    private EvidenceQualityAssessment.QualityStatus qualityStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_decision_status", length = 32)
    private EvidenceQualityAssessment.ProjectDecisionStatus projectDecisionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime_quality_boundary", length = 64)
    private EvidenceQualityAssessment.RuntimeQualityBoundary runtimeQualityBoundary;

    @Column(name = "quality_reasons", length = 512)
    private String qualityReasons;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "claim_generation", nullable = false)
    private long claimGeneration;

    @Column(name = "result_object_intent_bucket", length = 255)
    private String resultObjectIntentBucket;

    @Column(name = "result_object_intent_key", length = 1024)
    private String resultObjectIntentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_cleanup_status", nullable = false, length = 32)
    private AnalysisResultCleanupStatus resultCleanupStatus = AnalysisResultCleanupStatus.NOT_REQUIRED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Written at the terminal transition only; heartbeat/cleanup updatedAt is not terminal time.
    @Column(name = "terminal_at")
    private Instant terminalAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(Long sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public Long getResultFileId() {
        return resultFileId;
    }

    public void setResultFileId(Long resultFileId) {
        this.resultFileId = resultFileId;
    }

    public String getSourceBucket() {
        return sourceBucket;
    }

    public void setSourceBucket(String sourceBucket) {
        this.sourceBucket = sourceBucket;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public AnalysisJobStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisJobStatus status) {
        this.status = status;
    }

    public AnalysisMode getAnalysisMode() {
        return analysisMode;
    }

    public void setAnalysisMode(AnalysisMode analysisMode) {
        this.analysisMode = analysisMode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getResultObjectKey() {
        return resultObjectKey;
    }

    public void setResultObjectKey(String resultObjectKey) {
        this.resultObjectKey = resultObjectKey;
    }

    public String getResultPreview() {
        return resultPreview;
    }

    public void setResultPreview(String resultPreview) {
        this.resultPreview = resultPreview;
    }

    public String getAnalysisSummary() { return analysisSummary; }
    public void setAnalysisSummary(String analysisSummary) { this.analysisSummary = analysisSummary; }
    public String getDetectedComponents() { return detectedComponents; }
    public void setDetectedComponents(String detectedComponents) { this.detectedComponents = detectedComponents; }
    public String getDetectedRelationships() { return detectedRelationships; }
    public void setDetectedRelationships(String detectedRelationships) { this.detectedRelationships = detectedRelationships; }
    public String getAnalysisWarnings() { return analysisWarnings; }
    public void setAnalysisWarnings(String analysisWarnings) { this.analysisWarnings = analysisWarnings; }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getQualityContractVersion() { return qualityContractVersion; }
    public EvidenceQualityAssessment.TechnicalStatus getTechnicalStatus() { return technicalStatus; }
    public EvidenceQualityAssessment.KnowledgeStatus getKnowledgeStatus() { return knowledgeStatus; }
    public EvidenceQualityAssessment.QualityStatus getQualityStatus() { return qualityStatus; }
    public EvidenceQualityAssessment.ProjectDecisionStatus getProjectDecisionStatus() { return projectDecisionStatus; }
    public EvidenceQualityAssessment.RuntimeQualityBoundary getRuntimeQualityBoundary() { return runtimeQualityBoundary; }
    public String getQualityReasons() { return qualityReasons; }

    public void setQualityAssessment(EvidenceQualityAssessment assessment) {
        if (assessment == null) return;
        qualityContractVersion = assessment.contractVersion();
        technicalStatus = assessment.technicalStatus();
        knowledgeStatus = assessment.knowledgeStatus();
        qualityStatus = assessment.qualityStatus();
        projectDecisionStatus = assessment.projectDecisionStatus();
        runtimeQualityBoundary = assessment.runtimeQualityBoundary();
        qualityReasons = assessment.reasons().stream().map(Enum::name).sorted()
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    public List<EvidenceQualityAssessment.Reason> qualityReasonValues() {
        if (qualityReasons == null || qualityReasons.isBlank()) return List.of();
        return Arrays.stream(qualityReasons.split(","))
                .map(EvidenceQualityAssessment.Reason::valueOf).toList();
    }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public void setLeaseExpiresAt(Instant leaseExpiresAt) { this.leaseExpiresAt = leaseExpiresAt; }
    public long getClaimGeneration() { return claimGeneration; }
    public void setClaimGeneration(long claimGeneration) { this.claimGeneration = claimGeneration; }
    public String getResultObjectIntentBucket() { return resultObjectIntentBucket; }
    public void setResultObjectIntentBucket(String resultObjectIntentBucket) { this.resultObjectIntentBucket = resultObjectIntentBucket; }
    public String getResultObjectIntentKey() { return resultObjectIntentKey; }
    public void setResultObjectIntentKey(String resultObjectIntentKey) { this.resultObjectIntentKey = resultObjectIntentKey; }
    public AnalysisResultCleanupStatus getResultCleanupStatus() { return resultCleanupStatus; }
    public void setResultCleanupStatus(AnalysisResultCleanupStatus resultCleanupStatus) { this.resultCleanupStatus = resultCleanupStatus; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getTerminalAt() { return terminalAt; }
    public void setTerminalAt(Instant terminalAt) { this.terminalAt = terminalAt; }

    void clearLease() {
        leaseExpiresAt = null;
        nextAttemptAt = null;
    }
}
