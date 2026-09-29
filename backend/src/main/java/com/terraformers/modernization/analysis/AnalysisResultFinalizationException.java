package com.terraformers.modernization.analysis;

import com.terraformers.modernization.storage.ObjectReference;

/**
 * Signals a failure inside the relational success-finalization transaction body.
 * The transaction will roll back, so the result object written immediately before it is eligible
 * for compensation. Commit-phase failures outside the method body are intentionally not wrapped.
 */
public class AnalysisResultFinalizationException extends RuntimeException {

    private final ObjectReference reference;
    private final boolean cleanupCompleted;

    public AnalysisResultFinalizationException(RuntimeException cause, ObjectReference reference,
            boolean cleanupCompleted) {
        super("analysis result finalization failed", cause);
        this.reference = reference;
        this.cleanupCompleted = cleanupCompleted;
    }

    public ObjectReference reference() { return reference; }
    public boolean cleanupCompleted() { return cleanupCompleted; }
}
