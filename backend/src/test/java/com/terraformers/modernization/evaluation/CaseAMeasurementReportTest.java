package com.terraformers.modernization.evaluation;

import static com.terraformers.modernization.evaluation.CaseATestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseAMeasurementReportTest {
    @Test void createsMachineReadableCountsWithoutReplacingRawRun() {
        var definition = definition("vpc", EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM,
                List.of("decision"), List.of("aws_vpc", "aws_security_group"));
        var dataset = new EvaluationDataset("m3-evaluation-v1", "dataset", "test", List.of(definition));
        var trace = trace("run", "vpc", EvaluationStageStatus.PASS, List.of(hit(1, "schema", "aws_vpc")),
                List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"), true);
        var run = new EvaluationRunResult("m3-evaluation-v1", "dataset", "run", identity(), List.of(trace));
        var report = new CaseAMeasurementReporter().create(run, dataset);
        assertThat(report.reportSchemaVersion()).isEqualTo("case-a-retrieval-grounding-report-v1");
        assertThat(report.sourceRunId()).isEqualTo("run"); assertThat(report.caseCount()).isOne();
        assertThat(report.counts().groundingGap()).isOne(); assertThat(report.counts().groundingGapWithValidOutput()).isOne();
        assertThat(report.counts().retrievalPass()).isOne(); assertThat(run.traces()).containsExactly(trace);
    }
}
