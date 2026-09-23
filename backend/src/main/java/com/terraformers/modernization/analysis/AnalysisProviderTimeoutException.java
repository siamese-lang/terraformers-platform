package com.terraformers.modernization.analysis;

/** Provider-neutral signal that an analysis provider call exceeded its time limit. */
public class AnalysisProviderTimeoutException extends RuntimeException {

    public AnalysisProviderTimeoutException(Throwable cause) {
        super("analysis provider call timed out", cause);
    }
}
