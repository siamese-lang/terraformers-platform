package com.terraformers.modernization.reference;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Corpus-independent, bounded retrieval request for facts or generated resources. */
public record ReferenceQuery(String text, List<String> resourceTypes, int limit, boolean resourceOnly) {

    public static final int MAX_RESOURCE_TYPES = 16;
    private static final Pattern RESOURCE_TYPE = Pattern.compile("\\baws_[a-z0-9_]+\\b");

    public ReferenceQuery {
        text = text == null ? "" : text.strip();
        resourceTypes = resourceTypes == null
                ? List.of()
                : resourceTypes.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::strip)
                        .peek(ReferenceQuery::requireCanonicalResourceType)
                        .distinct()
                        .limit(MAX_RESOURCE_TYPES)
                        .toList();
        if (text.isBlank()) {
            throw new IllegalArgumentException("reference query text must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("reference query limit must be positive");
        }
    }

    public ReferenceQuery(String text, int limit) {
        this(text, extractResourceTypes(text), limit);
    }

    public ReferenceQuery(String text, List<String> resourceTypes, int limit) {
        this(text, resourceTypes, limit, false);
    }

    private static List<String> extractResourceTypes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher matcher = RESOURCE_TYPE.matcher(text);
        java.util.ArrayList<String> resources = new java.util.ArrayList<>();
        while (matcher.find() && resources.size() < MAX_RESOURCE_TYPES) {
            String value = matcher.group();
            if (!resources.contains(value)) {
                resources.add(value);
            }
        }
        return List.copyOf(resources);
    }

    private static void requireCanonicalResourceType(String resourceType) {
        if (!RESOURCE_TYPE.matcher(resourceType).matches()) {
            throw new IllegalArgumentException(
                    "reference query resource type must be a Terraform AWS provider identifier: " + resourceType);
        }
    }
}
