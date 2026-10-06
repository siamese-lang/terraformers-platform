package com.terraformers.modernization.analysis;

import java.util.List;
import java.util.Objects;

/** A bounded, application-owned reduction of Terraform CLI diagnostics. */
public record TerraformDiagnosticSummary(
        List<DiagnosticClass> diagnosticClasses,
        int errorCount,
        int warningCount
) {
    static final int MAX_COUNT = 1_000;

    public TerraformDiagnosticSummary {
        diagnosticClasses = diagnosticClasses == null
                ? List.of(DiagnosticClass.UNKNOWN)
                : diagnosticClasses.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();
        if (diagnosticClasses.isEmpty()) diagnosticClasses = List.of(DiagnosticClass.UNKNOWN);
        errorCount = bounded(errorCount);
        warningCount = bounded(warningCount);
    }

    private static int bounded(int count) {
        return Math.max(0, Math.min(count, MAX_COUNT));
    }

    public enum DiagnosticClass {
        CONFIGURATION_SYNTAX,
        MISSING_REQUIRED_ARGUMENT,
        UNDECLARED_REFERENCE,
        UNKNOWN,
        UNSUPPORTED_ARGUMENT_OR_BLOCK
    }
}
