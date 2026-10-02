package com.terraformers.modernization.reference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable, request-bounded evidence derived from the executable AWS provider schema. */
public final class AwsProviderSchemaEvidence {

    private final Map<String, String> summaries;

    public AwsProviderSchemaEvidence(Map<String, String> summaries) {
        this.summaries = Collections.unmodifiableMap(new LinkedHashMap<>(summaries));
    }

    public boolean covers(String resourceType) {
        return summaries.containsKey(resourceType);
    }

    public Set<String> resourceTypes() {
        return summaries.keySet();
    }

    public Map<String, String> summaries() {
        return summaries;
    }

    public String promptText() {
        return summaries.entrySet().stream()
                .map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- none");
    }
}
