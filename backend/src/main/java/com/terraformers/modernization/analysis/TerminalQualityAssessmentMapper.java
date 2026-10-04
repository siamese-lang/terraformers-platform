package com.terraformers.modernization.analysis;

import static com.terraformers.modernization.analysis.EvidenceQualityAssessment.*;

import java.util.List;

/** Maps terminal mechanics onto the existing evidence-quality-v1 vocabulary. */
final class TerminalQualityAssessmentMapper {
    private TerminalQualityAssessmentMapper() {}

    static EvidenceQualityAssessment failure(Throwable failure) {
        Reason reason = reason(failure);
        if (providerReason(failure) == AnalysisProviderFailureReason.INPUT_REJECTED) {
            return assessment(TechnicalStatus.PASS, KnowledgeStatus.NOT_APPLICABLE,
                    QualityStatus.NOT_APPLICABLE, ProjectDecisionStatus.NOT_APPLICABLE, List.of());
        }
        return assessment(TechnicalStatus.FAIL, KnowledgeStatus.UNKNOWN, QualityStatus.UNKNOWN,
                ProjectDecisionStatus.UNKNOWN, reason == null ? List.of() : List.of(reason));
    }

    private static Reason reason(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AnalysisProviderFailureException provider) {
                return switch (provider.reason()) {
                    case OUTPUT_TRUNCATED -> Reason.PROVIDER_OUTPUT_TRUNCATED;
                    case CONTENT_BLOCKED -> Reason.PROVIDER_CONTENT_BLOCKED;
                    case EMPTY_RESPONSE -> Reason.PROVIDER_EMPTY_RESPONSE;
                    case RATE_LIMITED -> Reason.PROVIDER_RATE_LIMITED;
                    case PROVIDER_ERROR -> Reason.PROVIDER_ERROR;
                    case INPUT_REJECTED, RESPONSE_FORMAT -> null;
                };
            }
            if (current instanceof AnalysisProviderTimeoutException
                    || current instanceof java.net.SocketTimeoutException) return Reason.PROVIDER_TIMEOUT;
            if (current instanceof com.terraformers.modernization.reference.ArchitectureFactsExtractionException facts) {
                return switch (facts.reason()) {
                    case PROVIDER_CONTENT_BLOCKED -> Reason.PROVIDER_CONTENT_BLOCKED;
                    case PROVIDER_TIMEOUT -> Reason.PROVIDER_TIMEOUT;
                    case PROVIDER_RATE_LIMITED -> Reason.PROVIDER_RATE_LIMITED;
                    case PROVIDER_ERROR, PROVIDER_RUNTIME -> Reason.PROVIDER_ERROR;
                    case RESPONSE_TRUNCATED -> Reason.PROVIDER_OUTPUT_TRUNCATED;
                    case EMPTY_RESPONSE -> Reason.PROVIDER_EMPTY_RESPONSE;
                    case INVALID_RESPONSE, EMPTY_FACTS -> null;
                };
            }
            if (current instanceof TerraformValidationFailureException) return Reason.TERRAFORM_EXECUTABLE_FAILURE;
            current = current.getCause();
        }
        return null;
    }

    private static AnalysisProviderFailureReason providerReason(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AnalysisProviderFailureException provider) return provider.reason();
            current = current.getCause();
        }
        return null;
    }

    private static EvidenceQualityAssessment assessment(TechnicalStatus technical, KnowledgeStatus knowledge,
            QualityStatus quality, ProjectDecisionStatus decisions, List<Reason> reasons) {
        return new EvidenceQualityAssessment(CONTRACT_VERSION, technical, knowledge, quality, decisions,
                RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS, reasons,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
