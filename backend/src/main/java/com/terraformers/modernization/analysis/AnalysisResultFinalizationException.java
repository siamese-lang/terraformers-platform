package com.terraformers.modernization.analysis;

/**
 * Signals a failure inside the relational success-finalization transaction body.
 * The transaction will roll back, so the result object written immediately before it is eligible
 * for compensation. Commit-phase failures outside the method body are intentionally not wrapped.
 */
public class AnalysisResultFinalizationException extends RuntimeException {

    public AnalysisResultFinalizationException(RuntimeException cause) {
        super("analysis result finalization failed", cause);
    }
}
