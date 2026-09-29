package com.terraformers.modernization.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Repository-owned fixed fact snapshots; this loader never reads image fixtures. */
public record CaseAAlternativeProbeFixture(String schemaVersion, String datasetVersion, List<Snapshot> snapshots) {
    public CaseAAlternativeProbeFixture {
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        if (!"case-a-a3-fixed-facts-v1".equals(schemaVersion)) {
            throw new IllegalArgumentException("unexpected A3 fixed-facts schemaVersion");
        }
        if (snapshots.size() != 6) {
            throw new IllegalArgumentException("A3 fixed-facts fixture must contain exactly six snapshots");
        }
        if (snapshots.stream().map(Snapshot::snapshotId).distinct().count() != snapshots.size()) {
            throw new IllegalArgumentException("A3 fixed-facts snapshot IDs must be unique");
        }
    }

    public static CaseAAlternativeProbeFixture load(ObjectMapper mapper, Path path) {
        try {
            return mapper.readValue(Files.readString(path), CaseAAlternativeProbeFixture.class);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load A3 fixed-facts fixture: " + path, exception);
        }
    }

    public record Snapshot(String snapshotId, long sourceWorkflowRunId, String caseId,
                           ArchitectureRetrievalFacts facts) {}
}
