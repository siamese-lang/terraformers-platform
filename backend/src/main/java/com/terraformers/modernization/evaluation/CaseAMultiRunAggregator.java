package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.CaseAMultiRunReport.CaseCoverage;
import com.terraformers.modernization.evaluation.CaseAMultiRunReport.Frequency;
import com.terraformers.modernization.evaluation.CaseAMultiRunReport.Statistics;
import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CaseAMultiRunAggregator {
    public CaseAMultiRunReport aggregate(List<CaseAMeasurementReport> reports) {
        if (reports == null || reports.isEmpty()) throw new IllegalArgumentException("reports must not be empty");
        CaseAMeasurementReport first = reports.get(0);
        requireComplete(first.configuration());
        for (CaseAMeasurementReport report : reports) {
            if (!first.datasetVersion().equals(report.datasetVersion()) || !comparable(first.configuration(), report.configuration())) {
                throw new IllegalArgumentException("incompatible Case A report configuration");
            }
        }
        List<RetrievalGroundingAssessment> all = reports.stream().flatMap(r -> r.cases().stream()).toList();
        Map<String, List<RetrievalGroundingAssessment>> grouped = new LinkedHashMap<>();
        all.stream().filter(RetrievalGroundingAssessment::applicable)
                .forEach(value -> grouped.computeIfAbsent(value.caseId(), ignored -> new ArrayList<>()).add(value));
        Map<String, CaseCoverage> coverage = new LinkedHashMap<>();
        grouped.forEach((id, values) -> coverage.put(id, new CaseCoverage(
                values.stream().map(v -> v.projectDecisionCoverage().matched()).toList(),
                values.stream().map(v -> v.projectDecisionCoverage().total()).toList(),
                values.stream().map(v -> v.resourceTypeCoverage().matched()).toList(),
                values.stream().map(v -> v.resourceTypeCoverage().total()).toList())));
        return new CaseAMultiRunReport(CaseAMultiRunReport.SCHEMA_VERSION, reports.size(), first.datasetVersion(),
                first.configuration(), reports.stream().map(CaseAMeasurementReport::sourceRunId).toList(),
                frequency(all, true), frequency(all, false), stats(all.stream().map(RetrievalGroundingAssessment::factExtractionLatencyMs).toList()),
                stats(all.stream().map(RetrievalGroundingAssessment::retrievalLatencyMs).toList()),
                stats(all.stream().map(RetrievalGroundingAssessment::generationLatencyMs).toList()),
                stats(all.stream().map(RetrievalGroundingAssessment::observedEndToEndStageSumMs).toList()),
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(coverage)));
    }

    private Frequency frequency(List<RetrievalGroundingAssessment> all, boolean basic) {
        List<RetrievalGroundingAssessment> applicable = all.stream().filter(RetrievalGroundingAssessment::applicable).toList();
        int failures = (int) applicable.stream().filter(basic ? RetrievalGroundingAssessment::groundingGap
                : RetrievalGroundingAssessment::groundingGapWithValidOutput).count();
        return new Frequency(applicable.size(), failures,
                applicable.isEmpty() ? 0 : (double) failures / applicable.size());
    }

    private Statistics stats(List<Long> raw) {
        List<Long> values = raw.stream().filter(java.util.Objects::nonNull).sorted().toList();
        if (values.isEmpty()) return new Statistics(0, 0, 0, 0);
        int n = values.size();
        double median = n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
        return new Statistics(n, values.get(0), median, values.get(n - 1));
    }

    private void requireComplete(ConfigurationIdentity c) {
        if (c.factExtractionThinkingLevel() == null || c.factExtractionMaxOutputTokens() == null
                || c.generationMaxOutputTokens() == null) throw new IllegalArgumentException("Case A provenance is incomplete");
    }

    private boolean comparable(ConfigurationIdentity a, ConfigurationIdentity b) {
        requireComplete(b);
        return java.util.Objects.equals(a.corpusVersion(), b.corpusVersion())
                && java.util.Objects.equals(a.providerVersion(), b.providerVersion())
                && java.util.Objects.equals(a.analysisProvider(), b.analysisProvider())
                && java.util.Objects.equals(a.embeddingProvider(), b.embeddingProvider())
                && java.util.Objects.equals(a.retrievalMode(), b.retrievalMode())
                && java.util.Objects.equals(a.topK(), b.topK())
                && java.util.Objects.equals(a.generationModelId(), b.generationModelId())
                && java.util.Objects.equals(a.embeddingModelId(), b.embeddingModelId())
                && java.util.Objects.equals(a.factExtractionThinkingLevel(), b.factExtractionThinkingLevel())
                && java.util.Objects.equals(a.factExtractionMaxOutputTokens(), b.factExtractionMaxOutputTokens())
                && java.util.Objects.equals(a.generationMaxOutputTokens(), b.generationMaxOutputTokens());
    }
}
