package com.terraformers.modernization.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EvaluationDataset(
        String schemaVersion,
        String datasetVersion,
        String description,
        List<EvaluationCase> cases
) {
    public EvaluationDataset {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        description = requireText(description, "description");
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("cases must not be empty");
        }

        Set<String> caseIds = new HashSet<>();
        for (EvaluationCase evaluationCase : cases) {
            Objects.requireNonNull(evaluationCase, "case");
            if (!schemaVersion.equals(evaluationCase.schemaVersion())) {
                throw new IllegalArgumentException("case schemaVersion must match dataset schemaVersion");
            }
            if (!datasetVersion.equals(evaluationCase.datasetVersion())) {
                throw new IllegalArgumentException("case datasetVersion must match dataset datasetVersion");
            }
            if (!caseIds.add(evaluationCase.caseId())) {
                throw new IllegalArgumentException("duplicate evaluation caseId: " + evaluationCase.caseId());
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
