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

    public TerraformValidationFailureException(Category category, String safeMessage) {
        super(safeMessage);
        this.category = category;
    }

    public Category category() {
        return category;
    }

    public static TerraformValidationFailureException fromSafeReason(String reason) {
        for (Category category : Category.values()) {
            if (reason != null && reason.startsWith(category.name() + ":")) {
                return new TerraformValidationFailureException(category, reason);
            }
        }
        return new TerraformValidationFailureException(Category.INTERNAL, "INTERNAL: Terraform CLI failure");
    }
}
