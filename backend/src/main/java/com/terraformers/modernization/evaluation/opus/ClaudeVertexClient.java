package com.terraformers.modernization.evaluation.opus;

import com.fasterxml.jackson.databind.JsonNode;

/** Evaluation-only network seam. Implementations must never expose authorization metadata. */
@FunctionalInterface
public interface ClaudeVertexClient {
    ClaudeResponse rawPredict(JsonNode request);

    record ClaudeResponse(String text, String stopReason, Integer inputTokens,
                          Integer outputTokens, String model) {}
}
