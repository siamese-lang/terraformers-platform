package com.terraformers.modernization.analysis;

/** Provider-neutral failure categories that have stable application-facing behavior. */
public enum AnalysisProviderFailureReason {
    OUTPUT_TRUNCATED,
    INPUT_REJECTED,
    RESPONSE_FORMAT
}
