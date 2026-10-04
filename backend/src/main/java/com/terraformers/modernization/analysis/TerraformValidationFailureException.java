package com.terraformers.modernization.analysis;

public class TerraformValidationFailureException extends IllegalStateException {

    public enum Category {
        INIT_TIMEOUT,
        PROVIDER_CLOSURE,
        INIT_CONFIGURATION,
        VALIDATE_TIMEOUT,
        VALIDATE_CONFIGURATION,
        INTERNAL
    }

    private final Category category;
    private final TerraformDiagnosticSummary diagnosticSummary;

    public TerraformValidationFailureException(Category category, String safeMessage) {
        this(category, safeMessage, null);
    }

    public TerraformValidationFailureException(Category category, String safeMessage,
                                                TerraformDiagnosticSummary diagnosticSummary) {
        super(safeMessage);
        this.category = category;
        this.diagnosticSummary = diagnosticSummary;
    }

    public Category category() {
        return category;
    }

    public TerraformDiagnosticSummary diagnosticSummary() {
        return diagnosticSummary;
    }

    public static TerraformValidationFailureException fromSafeReason(String reason) {
        return fromSafeReason(reason, null);
    }

    public static TerraformValidationFailureException fromSafeReason(
            String reason, TerraformDiagnosticSummary diagnosticSummary) {
        for (Category category : Category.values()) {
            if (reason != null && reason.startsWith(category.name() + ":")) {
                return new TerraformValidationFailureException(category, reason,
                        category == Category.VALIDATE_CONFIGURATION ? diagnosticSummary : null);
            }
        }
        return new TerraformValidationFailureException(Category.INTERNAL, "INTERNAL: Terraform CLI failure");
    }
}
