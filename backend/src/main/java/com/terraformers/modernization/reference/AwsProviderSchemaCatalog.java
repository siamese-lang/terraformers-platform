package com.terraformers.modernization.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class AwsProviderSchemaCatalog {

    public static final String PROVIDER_KEY = "registry.terraform.io/hashicorp/aws";
    public static final Path DEFAULT_CATALOG = Path.of("/opt/terraform-provider-schema/aws-5.100.0.json");
    private static final Pattern AWS_RESOURCE = Pattern.compile("aws_[a-z0-9_]+");
    private final JsonNode resources;

    @Autowired
    public AwsProviderSchemaCatalog(ObjectMapper mapper) {
        this(mapper, DEFAULT_CATALOG);
    }

    public AwsProviderSchemaCatalog(ObjectMapper mapper, Path catalog) {
        try {
            JsonNode root = mapper.readTree(catalog.toFile());
            resources = root.path("provider_schemas").path(PROVIDER_KEY).path("resource_schemas");
            if (!resources.isObject() || !resources.has("aws_vpc")) {
                throw new IllegalStateException("AWS 5.100.0 provider schema catalog is invalid");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("AWS 5.100.0 provider schema catalog cannot be loaded", exception);
        }
    }

    public boolean contains(String resourceType) {
        return validIdentifier(resourceType) && resources.has(resourceType);
    }

    public AwsProviderSchemaEvidence resolve(Collection<String> candidates) {
        TreeSet<String> normalized = new TreeSet<>();
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate == null || !validIdentifier(candidate.strip())) {
                    throw new IllegalArgumentException("candidate is not an AWS resource identifier");
                }
                String type = candidate.strip();
                if (!resources.has(type)) {
                    throw new IllegalArgumentException("candidate is absent from AWS 5.100.0 provider schema: " + type);
                }
                normalized.add(type);
            }
        }
        Map<String, String> summaries = new LinkedHashMap<>();
        normalized.forEach(type -> summaries.put(type, summarize(resources.path(type).path("block"))));
        return new AwsProviderSchemaEvidence(summaries);
    }

    private boolean validIdentifier(String candidate) {
        return candidate != null && AWS_RESOURCE.matcher(candidate).matches();
    }

    private String summarize(JsonNode block) {
        StringBuilder result = new StringBuilder();
        appendAttributes(result, block.path("attributes"));
        appendBlocks(result, block.path("block_types"));
        return result.length() == 0 ? "no configurable arguments" : result.toString();
    }

    private void appendAttributes(StringBuilder result, JsonNode attributes) {
        if (!attributes.isObject()) return;
        attributes.fieldNames().forEachRemaining(name -> {
            JsonNode attribute = attributes.path(name);
            if (attribute.path("computed").asBoolean(false)
                    && !attribute.path("optional").asBoolean(false)
                    && !attribute.path("required").asBoolean(false)) return;
            appendSeparator(result);
            result.append(name).append(": type=")
                    .append(attribute.path("type").toString())
                    .append(" (")
                    .append(attribute.path("required").asBoolean(false) ? "required" : "optional")
                    .append(')');
        });
    }

    private void appendBlocks(StringBuilder result, JsonNode blocks) {
        if (!blocks.isObject()) return;
        blocks.fieldNames().forEachRemaining(name -> {
            JsonNode nested = blocks.path(name);
            appendSeparator(result);
            result.append(name).append(" block(nesting=")
                    .append(nested.path("nesting_mode").asText("unknown"))
                    .append(", ")
                    .append(nested.path("min_items").asInt(0) > 0 ? "required" : "optional")
                    .append(", arguments=[");
            StringBuilder nestedSummary = new StringBuilder();
            appendAttributes(nestedSummary, nested.path("block").path("attributes"));
            appendBlocks(nestedSummary, nested.path("block").path("block_types"));
            result.append(nestedSummary).append("])");
        });
    }

    private void appendSeparator(StringBuilder result) {
        if (result.length() > 0) result.append(", ");
    }
}
