package com.terraformers.modernization.evaluation;

import static com.terraformers.modernization.evaluation.CaseATestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseAMultiRunAggregatorTest {
    @Test void aggregatesThreeComparableRunsAndPreservesCoverageVariation() throws Exception {
        var reports = List.of(report("r1", 0, 2, true, 10), report("r2", 0, 2, true, 30), report("r3", 1, 4, false, 20));
        var aggregate = new CaseAMultiRunAggregator().aggregate(reports);
        assertThat(aggregate.runCount()).isEqualTo(3);
        assertThat(aggregate.factExtractionLatencyMs()).isEqualTo(new CaseAMultiRunReport.Statistics(3, 10, 20, 30));
        assertThat(aggregate.groundingGap()).isEqualTo(new CaseAMultiRunReport.Frequency(3, 2, 2.0 / 3));
        assertThat(aggregate.caseCoverage().get("vpc").resourceTypeMatched()).containsExactly(2, 2, 4);
        String json = new ObjectMapper().writeValueAsString(aggregate);
        assertThat(json).doesNotContainIgnoringCase("p95").doesNotContainIgnoringCase("p99");
    }
    @Test void rejectsIncompatibleIdentity() {
        var valid = report("r1", 0, 2, true, 10);
        var badIdentity = new EvaluationTrace.ConfigurationIdentity("terraformers-reference-v3", "5.100.0", "vertex", "vertex", "REQUIRED", 9, "gemini-3.8-flash", "gemini-embedding-001", "hash", "LOW", 800, 8192);
        var incompatible = new CaseAMeasurementReport(valid.reportSchemaVersion(), "r2", valid.datasetVersion(), badIdentity,
                valid.caseCount(), valid.cases(), valid.counts());
        assertThatThrownBy(() -> new CaseAMultiRunAggregator().aggregate(List.of(valid, incompatible)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("incompatible");
    }
    private CaseAMeasurementReport report(String run, int decisions, int resources, boolean gap, long latency) {
        var coverageDecision = new RetrievalGroundingAssessment.Coverage(decisions, 1, (double) decisions, java.util.Map.of(), List.of());
        var coverageResources = new RetrievalGroundingAssessment.Coverage(resources, 4, resources / 4.0, java.util.Map.of(), List.of());
        var assessment = new RetrievalGroundingAssessment("vpc", true, EvaluationStageStatus.PASS, "q", List.of(), 8, List.of(),
                coverageDecision, coverageResources, EvaluationStageStatus.PASS, 4, 4, 0, EvaluationStageStatus.PASS,
                true, null, gap, gap, latency, 20L, 30L, latency + 55);
        return new CaseAMeasurementReport(CaseAMeasurementReport.SCHEMA_VERSION, run, "dataset", identity(), 1,
                List.of(assessment), new CaseAMeasurementReport.Counts(1,0,1,0,0,gap?1:0,gap?1:0,0,0,1,0));
    }
}
