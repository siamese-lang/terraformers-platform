package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisJobResponseQualityTest {
    @Test
    void terminalDurationUsesTerminalTimeAndLeavesLegacyOrInvalidTimingUnknown() {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.prePersist();
        var accepted = entity.getCreatedAt();
        entity.setTerminalAt(accepted.plusMillis(123464));
        assertThat(AnalysisJobResponse.from(entity).timing().terminalAt()).isNull();
        assertThat(AnalysisJobResponse.from(entity).timing().acceptedToTerminalMs()).isNull();

        entity.setStatus(AnalysisJobStatus.SUCCEEDED);
        assertThat(AnalysisJobResponse.from(entity).timing().acceptedAt()).isEqualTo(accepted);
        assertThat(AnalysisJobResponse.from(entity).timing().acceptedToTerminalMs()).isEqualTo(123464L);
        entity.preUpdate();
        assertThat(AnalysisJobResponse.from(entity).timing().acceptedToTerminalMs()).isEqualTo(123464L);

        entity.setStatus(AnalysisJobStatus.FAILED);
        assertThat(AnalysisJobResponse.from(entity).timing().acceptedToTerminalMs()).isEqualTo(123464L);
        entity.setTerminalAt(null);
        assertThat(AnalysisJobResponse.from(entity).timing().acceptedToTerminalMs()).isNull();
        entity.setTerminalAt(accepted.minusSeconds(1));
        assertThat(AnalysisJobResponse.from(entity).timing().acceptedToTerminalMs()).isNull();
    }

    @Test
    void legacyRowHasNullQualityAndPersistedSnapshotUsesOnlyBoundedFields() {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        assertThat(AnalysisJobResponse.from(entity).quality()).isNull();
        entity.setQualityAssessment(new EvidenceQualityAssessment(EvidenceQualityAssessment.CONTRACT_VERSION,
                EvidenceQualityAssessment.TechnicalStatus.PASS,
                EvidenceQualityAssessment.KnowledgeStatus.UNKNOWN,
                EvidenceQualityAssessment.QualityStatus.UNKNOWN,
                EvidenceQualityAssessment.ProjectDecisionStatus.UNKNOWN,
                EvidenceQualityAssessment.RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                List.of(), List.of("aws_vpc"), List.of(), List.of(), List.of("aws_vpc"),
                List.of(), List.of(), List.of(), List.of()));

        AnalysisJobResponse.Quality quality = AnalysisJobResponse.from(entity).quality();
        assertThat(quality.contractVersion()).isEqualTo("evidence-quality-v1");
        assertThat(quality.qualityStatus()).isEqualTo(EvidenceQualityAssessment.QualityStatus.UNKNOWN);
        assertThat(quality.reasons()).isEmpty();
        assertThat(quality.getClass().getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("contractVersion", "technicalStatus", "knowledgeStatus", "qualityStatus",
                        "projectDecisionStatus", "runtimeQualityBoundary", "reasons");
    }
}
