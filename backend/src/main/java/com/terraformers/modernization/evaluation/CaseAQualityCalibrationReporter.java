package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.analysis.EvidenceQualityAssessment.QualityStatus;
import com.terraformers.modernization.evaluation.CaseAQualityCalibrationReport.CaseResult;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CaseAQualityCalibrationReporter {
    private final CaseAMeasurementReporter measurementReporter = new CaseAMeasurementReporter();
    private final CaseAQualityCalibrationScorer scorer = new CaseAQualityCalibrationScorer();

    public CaseAQualityCalibrationReport create(EvaluationRunResult run, EvaluationDataset dataset) {
        return create(run, dataset, Map.of());
    }

    public CaseAQualityCalibrationReport create(EvaluationRunResult run, EvaluationDataset dataset,
            Map<String, QualityStatus> runtimeQualityStatuses) {
        if (!run.datasetVersion().equals(dataset.datasetVersion())) throw new IllegalArgumentException("datasetVersion mismatch");
        Map<String, EvaluationCase> definitions = dataset.cases().stream()
                .collect(Collectors.toMap(EvaluationCase::caseId, item -> item));
        List<CaseResult> cases = run.traces().stream().map(trace -> {
            EvaluationCase definition = definitions.get(trace.caseId());
            if (definition == null) throw new IllegalArgumentException("dataset has no case " + trace.caseId());
            return scorer.score(definition, trace, runtimeQualityStatuses.get(trace.caseId()));
        }).toList();
        int technical = count(cases, CaseResult::technicalSuccess);
        int labeled = count(cases, CaseResult::labeledQualitySuccess);
        int architecture = count(cases, CaseResult::architectureCase);
        var counts = new CaseAQualityCalibrationReport.Counts(cases.size(), technical, cases.size() - technical,
                labeled, cases.size() - labeled, count(cases, CaseResult::falseGreen), architecture,
                cases.size() - architecture);
        return new CaseAQualityCalibrationReport(CaseAQualityCalibrationReport.SCHEMA_VERSION, run.runId(),
                run.datasetVersion(), run.configuration(), measurementReporter.create(run, dataset), cases, counts);
    }

    private int count(List<CaseResult> cases, Predicate<CaseResult> predicate) {
        return (int) cases.stream().filter(predicate).count();
    }
}
