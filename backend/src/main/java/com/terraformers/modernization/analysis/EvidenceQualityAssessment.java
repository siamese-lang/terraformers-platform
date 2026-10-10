package com.terraformers.modernization.analysis;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Versioned, bounded representation of Terraformers runtime evidence quality.
 *
 * <p>This contract is intentionally independent from AnalysisJob persistence. A7-4 may persist
 * this representation without silently changing the A7-1 decision rules.</p>
 */
public record EvidenceQualityAssessment(
        String contractVersion,
        TechnicalStatus technicalStatus,
        KnowledgeStatus knowledgeStatus,
        QualityStatus qualityStatus,
        ProjectDecisionStatus projectDecisionStatus,
        RuntimeQualityBoundary runtimeQualityBoundary,
        List<Reason> reasons,
        List<String> extractedResourceTypes,
        List<String> missingOfficialKnowledgeResourceTypes,
        List<String> missingSelectedEvidenceResourceTypes,
        List<String> generatedResourceTypes,
        List<String> generatedResourcesAbsentFromProviderSchema,
        List<String> generatedResourcesWithoutSelectedEvidence,
        List<String> requiredProjectDecisionIds,
        List<String> missingProjectDecisionIds
) {
    public static final String CONTRACT_VERSION = "evidence-quality-v1";

    public EvidenceQualityAssessment {
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unsupported evidence quality contract version: " + contractVersion);
        }
        technicalStatus = Objects.requireNonNull(technicalStatus, "technicalStatus");
        knowledgeStatus = Objects.requireNonNull(knowledgeStatus, "knowledgeStatus");
        qualityStatus = Objects.requireNonNull(qualityStatus, "qualityStatus");
        projectDecisionStatus = Objects.requireNonNull(projectDecisionStatus, "projectDecisionStatus");
        runtimeQualityBoundary = Objects.requireNonNull(runtimeQualityBoundary, "runtimeQualityBoundary");
        if (runtimeQualityBoundary != RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS) {
            throw new IllegalArgumentException("runtime quality boundary must remain conditional on extracted facts");
        }
        reasons = immutableReasons(reasons);
        extractedResourceTypes = immutableStrings(extractedResourceTypes);
        missingOfficialKnowledgeResourceTypes = immutableStrings(missingOfficialKnowledgeResourceTypes);
        missingSelectedEvidenceResourceTypes = immutableStrings(missingSelectedEvidenceResourceTypes);
        generatedResourceTypes = immutableStrings(generatedResourceTypes);
        generatedResourcesAbsentFromProviderSchema = immutableStrings(generatedResourcesAbsentFromProviderSchema);
        generatedResourcesWithoutSelectedEvidence = immutableStrings(generatedResourcesWithoutSelectedEvidence);
        requiredProjectDecisionIds = immutableStrings(requiredProjectDecisionIds);
        missingProjectDecisionIds = immutableStrings(missingProjectDecisionIds);
    }

    private static List<String> immutableStrings(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.strip());
            }
        }
        return List.copyOf(normalized);
    }

    private static List<Reason> immutableReasons(Collection<Reason> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        EnumSet<Reason> normalized = EnumSet.noneOf(Reason.class);
        for (Reason value : values) {
            normalized.add(Objects.requireNonNull(value, "reason"));
        }
        return List.copyOf(normalized);
    }

    public enum TechnicalStatus {
        PASS,
        FAIL
    }

    public enum KnowledgeStatus {
        COMPLETE,
        INCOMPLETE,
        UNKNOWN,
        NOT_APPLICABLE
    }

    public enum QualityStatus {
        EVIDENCE_BACKED,
        DEGRADED,
        UNKNOWN,
        NOT_APPLICABLE
    }

    public enum ProjectDecisionStatus {
        COMPLETE,
        INCOMPLETE,
        UNKNOWN,
        NOT_APPLICABLE
    }

    public enum RuntimeQualityBoundary {
        CONDITIONAL_ON_EXTRACTED_FACTS
    }

    /**
     * ADR-008 bounded reason vocabulary. A7-1 emits only evidence/knowledge reasons; later phases
     * own provider and executable-failure classification.
     */
    public enum Reason {
        RESOURCE_UNKNOWN_TO_PROVIDER,
        OFFICIAL_KNOWLEDGE_NOT_AVAILABLE,
        REQUIRED_EVIDENCE_NOT_RETRIEVED,
        REQUIRED_PROJECT_DECISION_NOT_RETRIEVED,
        GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE,
        CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING,
        PROVIDER_CONTENT_BLOCKED,
        PROVIDER_OUTPUT_TRUNCATED,
        PROVIDER_EMPTY_RESPONSE,
        PROVIDER_TIMEOUT,
        PROVIDER_RATE_LIMITED,
        PROVIDER_ERROR,
        PROVIDER_SCHEMA_FAILURE,
        TERRAFORM_EXECUTABLE_FAILURE
    }
}
