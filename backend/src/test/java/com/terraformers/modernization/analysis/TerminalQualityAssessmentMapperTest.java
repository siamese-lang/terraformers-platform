package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class TerminalQualityAssessmentMapperTest {
    @Test
    void mapsOnlyExactBoundedFailureReasons() {
        assertReason(AnalysisProviderFailureReason.CONTENT_BLOCKED,
                EvidenceQualityAssessment.Reason.PROVIDER_CONTENT_BLOCKED);
        assertReason(AnalysisProviderFailureReason.OUTPUT_TRUNCATED,
                EvidenceQualityAssessment.Reason.PROVIDER_OUTPUT_TRUNCATED);
        assertReason(AnalysisProviderFailureReason.EMPTY_RESPONSE,
                EvidenceQualityAssessment.Reason.PROVIDER_EMPTY_RESPONSE);
        assertReason(AnalysisProviderFailureReason.RATE_LIMITED,
                EvidenceQualityAssessment.Reason.PROVIDER_RATE_LIMITED);
        assertReason(AnalysisProviderFailureReason.PROVIDER_ERROR,
                EvidenceQualityAssessment.Reason.PROVIDER_ERROR);
        assertThat(TerminalQualityAssessmentMapper.failure(new SocketTimeoutException()).reasons())
                .containsExactly(EvidenceQualityAssessment.Reason.PROVIDER_TIMEOUT);
        assertThat(TerminalQualityAssessmentMapper.failure(new TerraformValidationFailureException(
                TerraformValidationFailureException.Category.INTERNAL, "safe")).reasons())
                .containsExactly(EvidenceQualityAssessment.Reason.TERRAFORM_EXECUTABLE_FAILURE);
        assertThat(TerminalQualityAssessmentMapper.failure(provider(AnalysisProviderFailureReason.RESPONSE_FORMAT)).reasons())
                .isEmpty();
    }

    @Test
    void inputRejectionIsTechnicallyValidAndNotApplicable() {
        var quality = TerminalQualityAssessmentMapper.failure(provider(AnalysisProviderFailureReason.INPUT_REJECTED));
        assertThat(quality.technicalStatus()).isEqualTo(EvidenceQualityAssessment.TechnicalStatus.PASS);
        assertThat(quality.knowledgeStatus()).isEqualTo(EvidenceQualityAssessment.KnowledgeStatus.NOT_APPLICABLE);
        assertThat(quality.qualityStatus()).isEqualTo(EvidenceQualityAssessment.QualityStatus.NOT_APPLICABLE);
        assertThat(quality.projectDecisionStatus())
                .isEqualTo(EvidenceQualityAssessment.ProjectDecisionStatus.NOT_APPLICABLE);
        assertThat(quality.reasons()).isEmpty();
    }

    private void assertReason(AnalysisProviderFailureReason failure, EvidenceQualityAssessment.Reason reason) {
        assertThat(TerminalQualityAssessmentMapper.failure(provider(failure)).reasons()).containsExactly(reason);
    }

    private AnalysisProviderFailureException provider(AnalysisProviderFailureReason reason) {
        return new AnalysisProviderFailureException(reason, new IllegalStateException());
    }
}
