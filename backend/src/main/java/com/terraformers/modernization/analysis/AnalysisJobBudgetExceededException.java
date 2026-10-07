package com.terraformers.modernization.analysis;

/** Accepted-age deadline, distinct from an observed provider timeout (a queued job may have no call). */
public final class AnalysisJobBudgetExceededException extends RuntimeException {
    public AnalysisJobBudgetExceededException() { super("analysis original accepted-age cutoff reached"); }
}
