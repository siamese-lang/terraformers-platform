package com.terraformers.modernization.analysis;

public enum AnalysisTelemetryStage {
    ANALYSIS_EXECUTION("analysis_execution"),
    RESULT_FINALIZE("result_finalize"),
    COMPENSATION("compensation");

    private final String tag;

    AnalysisTelemetryStage(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
