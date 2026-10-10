package com.terraformers.modernization.analysis;

import java.util.List;
import java.util.Objects;

/** A bounded, application-owned reduction of Terraform CLI diagnostics. */
public record TerraformDiagnosticSummary(
        List<DiagnosticClass> diagnosticClasses,
        int errorCount,
        int warningCount,
        List<Detail> details
) {
    public TerraformDiagnosticSummary(List<DiagnosticClass> diagnosticClasses, int errorCount, int warningCount) {
        this(diagnosticClasses, errorCount, warningCount, List.of());
    }
    /** Values and snippets are intentionally excluded. Summary is chosen from an application allowlist. */
    public record Detail(DiagnosticClass diagnosticClass, String summary, Integer line, Integer column) {}
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
        details = details == null ? List.of() : details.stream().limit(32).toList();
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
