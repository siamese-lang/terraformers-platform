package com.terraformers.modernization.analysis;

/** A provider failure whose application-facing category is independent of its implementation. */
public class AnalysisProviderFailureException extends RuntimeException {

    private final AnalysisProviderFailureReason reason;

    public AnalysisProviderFailureException(AnalysisProviderFailureReason reason, Throwable cause) {
        super("analysis provider failure: " + reason, cause);
        this.reason = reason;
    }

    public AnalysisProviderFailureReason reason() {
        return reason;
    }
}
