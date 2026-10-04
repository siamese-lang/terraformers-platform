package com.terraformers.modernization.analysis;

public record TerraformDraftValidation(
        boolean valid,
        String sanitizedContent,
        String reason,
        TerraformDiagnosticSummary diagnosticSummary
) {
    public TerraformDraftValidation(boolean valid, String sanitizedContent, String reason) {
        this(valid, sanitizedContent, reason, null);
    }
}
