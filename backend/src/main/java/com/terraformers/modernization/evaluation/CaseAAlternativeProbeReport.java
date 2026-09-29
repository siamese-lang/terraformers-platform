package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.RetrievalGroundingAssessment.Coverage;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import java.util.List;
import java.util.Map;

public record CaseAAlternativeProbeReport(
        String schemaVersion, String sourceCommit, String datasetVersion, String corpusVersion,
        String providerVersion, String embeddingModel, int embeddingDimension,
        List<SnapshotResult> snapshots, Map<String, Aggregate> aggregates
) {
    public record SnapshotResult(
            String snapshotId, long sourceWorkflowRunId, String caseId, ArchitectureRetrievalFacts facts,
            String currentQuery, List<String> currentQueryResourceFilters, String currentEmbeddingSha256,
            String relationshipFirstQuery, String relationshipFirstEmbeddingSha256, List<StrategyResult> strategies
    ) {}

    public record StrategyResult(
            String strategyId, int candidateK, int selectedK, String resourceFilterMode,
            long openSearchLatencyMs, int candidatePoolDocumentCount, int selectedDocumentCount,
            List<Hit> candidateHits, List<Hit> selectedHits, long candidateContentCharacters,
            long selectedContentCharacters, Coverage projectDecisionCoverage, Coverage resourceTypeCoverage,
            boolean complete
    ) {}

    public record Hit(int rank, String documentId, double score, String authority, int priority,
                      List<String> resourceTypes, List<String> riskTags, int contentCharacters) {}

    public record Aggregate(
            int vpcCompleteSnapshots, int vpcSnapshotTotal, int controlSnapshotsWithRetrievalRegression,
            int controlSnapshotTotal, int projectDecisionsMatched, int projectDecisionsTotal,
            int resourceTypesMatched, int resourceTypesTotal, Range latencyMs,
            Range candidateContentCharacters, Range selectedContentCharacters
    ) {}

    public record Range(long min, long median, long max) {}
}
