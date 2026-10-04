package com.terraformers.modernization.analysis;

/** Provider-neutral failure categories that have stable application-facing behavior. */
public enum AnalysisProviderFailureReason {
    OUTPUT_TRUNCATED,
    CONTENT_BLOCKED,
    EMPTY_RESPONSE,
    RATE_LIMITED,
    PROVIDER_ERROR,
    INPUT_REJECTED,
    RESPONSE_FORMAT
}
