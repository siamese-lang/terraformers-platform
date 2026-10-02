package com.terraformers.modernization.analysis;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TerraformDraftValidator {

    private static final Pattern RESOURCE_OR_MODULE = Pattern.compile(
            "(?m)^\\s*(resource|module)\\s+\\\"[^\\\"]+\\\"");

    public TerraformDraftValidation validate(String candidate) {
        String sanitized = stripMarkdownFences(candidate);
        if (sanitized.isBlank()) {
            return invalid(sanitized, "generated Terraform is blank");
        }
        String normalized = sanitized.toLowerCase(Locale.ROOT);
        if (normalized.equals("terraform") || normalized.equals("hcl")) {
            return invalid(sanitized, "generated Terraform contains only a language label");
        }
        if (!RESOURCE_OR_MODULE.matcher(sanitized).find()) {
            return invalid(sanitized, "generated Terraform must contain at least one resource or module block");
        }
        if (looksLikeProse(normalized)) {
            return invalid(sanitized, "generated Terraform is not a Terraform draft");
        }
        return new TerraformDraftValidation(true, sanitized, null);
    }

    public String stripMarkdownFences(String candidate) {
        if (candidate == null) {
            return "";
        }
        String stripped = candidate.strip();
        if (!stripped.startsWith("```")) {
            return stripped;
        }
        stripped = stripped.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        return stripped.replaceFirst("\\s*```$", "").strip();
    }

    private boolean looksLikeProse(String normalized) {
        return normalized.startsWith("here is")
                || normalized.startsWith("the terraform")
                || normalized.contains("```")
                || normalized.contains("i cannot")
                || normalized.contains("as an ai");
    }

    private TerraformDraftValidation invalid(String sanitized, String reason) {
        return new TerraformDraftValidation(false, sanitized, reason);
    }
}
