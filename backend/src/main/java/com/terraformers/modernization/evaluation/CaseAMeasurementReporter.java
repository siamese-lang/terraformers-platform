package com.terraformers.modernization.evaluation;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CaseAMeasurementReporter {
    private final RetrievalGroundingScorer scorer = new RetrievalGroundingScorer();

    public CaseAMeasurementReport create(EvaluationRunResult run, EvaluationDataset dataset) {
        if (!run.datasetVersion().equals(dataset.datasetVersion())) throw new IllegalArgumentException("datasetVersion mismatch");
        Map<String, EvaluationCase> definitions = dataset.cases().stream()
                .collect(Collectors.toMap(EvaluationCase::caseId, item -> item));
        List<RetrievalGroundingAssessment> assessments = run.traces().stream().map(trace -> {
            EvaluationCase definition = definitions.get(trace.caseId());
            if (definition == null) throw new IllegalArgumentException("dataset has no case " + trace.caseId());
            return scorer.score(definition, trace);
        }).toList();
        int negativeTotal = 0, negativeCorrect = 0;
        for (EvaluationTrace trace : run.traces()) {
            EvaluationCase definition = definitions.get(trace.caseId());
            if (definition.expectedClassification() != EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM) {
                negativeTotal++;
                var evidence = trace.generation().evidence();
                if (trace.generation().status() == EvaluationStageStatus.PASS && evidence != null
                        && evidence.observedClassification() == definition.expectedClassification()
                        && evidence.terraformCode().isBlank()) negativeCorrect++;
            }
        }
        var counts = new CaseAMeasurementReport.Counts(
                stage(run, EvaluationTrace::factExtraction, EvaluationStageStatus.PASS),
                stage(run, EvaluationTrace::factExtraction, EvaluationStageStatus.FAIL),
                stage(run, EvaluationTrace::retrieval, EvaluationStageStatus.PASS),
                stage(run, EvaluationTrace::retrieval, EvaluationStageStatus.FAIL),
                stage(run, EvaluationTrace::retrieval, EvaluationStageStatus.NOT_RUN),
                count(assessments, RetrievalGroundingAssessment::groundingGap),
                count(assessments, RetrievalGroundingAssessment::groundingGapWithValidOutput),
                negativeCorrect, negativeTotal,
                stage(run, EvaluationTrace::validation, EvaluationStageStatus.PASS),
                stage(run, EvaluationTrace::validation, EvaluationStageStatus.FAIL));
        return new CaseAMeasurementReport(CaseAMeasurementReport.SCHEMA_VERSION, run.runId(), run.datasetVersion(),
                run.configuration(), assessments.size(), assessments, counts);
    }

    private int stage(EvaluationRunResult run,
            java.util.function.Function<EvaluationTrace, EvaluationTrace.StageTrace<?>> stage,
            EvaluationStageStatus status) {
        return (int) run.traces().stream().map(stage).filter(value -> value.status() == status).count();
    }

    private int count(List<RetrievalGroundingAssessment> values, Predicate<RetrievalGroundingAssessment> predicate) {
        return (int) values.stream().filter(predicate).count();
    }
}
