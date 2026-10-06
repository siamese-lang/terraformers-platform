package com.terraformers.modernization.reference;

import java.util.List;

public record ReferenceDocument(
        String id,
        String title,
        String content,
        double score,
        String documentType,
        List<String> resourceTypes,
        String sourcePath,
        String providerVersion,
        String corpusVersion,
        String authority,
        int priority,
        List<String> riskTags
) {
    /** The same official-document boundary used by evidence-quality-v1. */
    public boolean isOfficialProviderDocumentation() {
        return "PROVIDER_DOCUMENTATION".equals(authority)
                && ("AWS_PROVIDER_DOC".equals(documentType) || "AWS_PROVIDER_EXAMPLE".equals(documentType));
    }

    public ReferenceDocument {
        resourceTypes = resourceTypes == null ? List.of() : List.copyOf(resourceTypes);
        riskTags = riskTags == null ? List.of() : List.copyOf(riskTags);
    }

    public ReferenceDocument(String id, String title, String content, double score) {
        this(id, title, content, score, "", List.of(), "", "", "", "", 0, List.of());
    }
}
