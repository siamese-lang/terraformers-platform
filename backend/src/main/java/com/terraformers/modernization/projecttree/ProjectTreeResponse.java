package com.terraformers.modernization.projecttree;

import com.terraformers.modernization.analysis.AnalysisJobResponse;
import java.time.Instant;
import java.util.List;

public record ProjectTreeResponse(
        Long projectId,
        String displayName,
        String visibility,
        String latestAnalysisJobId,
        Long latestResultFileId,
        String latestResultObjectKey,
        String analysisStatus,
        AnalysisJobResponse.Quality quality,
        AnalysisJobResponse.Timing analysisTiming,
        String analysisSummary,
        List<String> detectedComponents,
        List<String> detectedRelationships,
        List<String> warnings,
        Instant updatedAt,
        List<ProjectTreeNode> tree
) {
}
