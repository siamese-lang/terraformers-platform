package com.terraformers.modernization.evaluation;

import java.util.Objects;

public record EvaluationFailure(
        EvaluationStage stage,
        EvaluationFailureCategory category,
        String detail
) {
    public EvaluationFailure {
        stage = Objects.requireNonNull(stage, "stage");
        category = Objects.requireNonNull(category, "category");
        detail = detail == null ? "" : detail.strip();
    }
}
