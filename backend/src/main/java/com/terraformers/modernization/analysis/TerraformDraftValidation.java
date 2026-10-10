package com.terraformers.modernization.analysis;

public record TerraformDraftValidation(boolean valid, String sanitizedContent, String reason,
        TerraformDiagnosticSummary diagnosticSummary, String executionPhase, Integer exitCode, Long elapsedMs) {
    public TerraformDraftValidation(boolean valid, String content, String reason) {
        this(valid, content, reason, null);
    }
    public TerraformDraftValidation(boolean valid, String content, String reason, TerraformDiagnosticSummary summary) {
        this(valid, content, reason, summary, null, null, null);
    }
    public TerraformDraftValidation withExecution(String phase, Integer code, long elapsed) {
        return new TerraformDraftValidation(valid, sanitizedContent, reason, diagnosticSummary, phase, code, elapsed);
    }
}
