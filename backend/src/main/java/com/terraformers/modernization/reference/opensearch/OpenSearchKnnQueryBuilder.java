package com.terraformers.modernization.reference.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchKnnQueryBuilder {

    private final ObjectMapper objectMapper;

    public OpenSearchKnnQueryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(String vectorFieldName, String contentFieldName, List<Float> vector, int topK) {
        return build(vectorFieldName, contentFieldName, vector, topK, "", "", List.of(), List.of());
    }

    public String build(
            String vectorFieldName,
            String contentFieldName,
            List<Float> vector,
            int topK,
            String corpusVersion,
            String providerVersion,
            List<String> resourceTypes
    ) {
        return build(vectorFieldName, contentFieldName, vector, topK, corpusVersion, providerVersion,
                resourceTypes, List.of());
    }

    public String build(
            String vectorFieldName,
            String contentFieldName,
            List<Float> vector,
            int topK,
            String corpusVersion,
            String providerVersion,
            List<String> resourceTypes,
            List<String> authorities
    ) {
        return build(vectorFieldName, contentFieldName, vector, topK, corpusVersion, providerVersion,
                resourceTypes, authorities, List.of());
    }

    public String build(
            String vectorFieldName, String contentFieldName, List<Float> vector, int topK,
            String corpusVersion, String providerVersion, List<String> resourceTypes,
            List<String> authorities, List<String> documentTypes
    ) {
        if (vectorFieldName == null || vectorFieldName.isBlank()) {
            throw new IllegalArgumentException("vector field name must be set");
        }
        if (contentFieldName == null || contentFieldName.isBlank()) {
            throw new IllegalArgumentException("content field name must be set");
        }
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("embedding vector must not be empty");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }

        List<Map<String, Object>> filters = new ArrayList<>();
        addTermFilter(filters, "corpusVersion", corpusVersion);
        addTermFilter(filters, "providerVersion", providerVersion);
        List<String> normalizedResourceTypes = resourceTypes == null
                ? List.of()
                : resourceTypes.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::strip)
                        .distinct()
                        .toList();
        if (!normalizedResourceTypes.isEmpty()) {
            filters.add(Map.of("terms", Map.of("resourceTypes", normalizedResourceTypes)));
        }
        List<String> normalizedAuthorities = normalize(authorities);
        if (!normalizedAuthorities.isEmpty()) {
            filters.add(Map.of("terms", Map.of("authority", normalizedAuthorities)));
        }
        List<String> normalizedDocumentTypes = normalize(documentTypes);
        if (!normalizedDocumentTypes.isEmpty()) {
            filters.add(Map.of("terms", Map.of("documentType", normalizedDocumentTypes)));
        }

        Map<String, Object> knnParameters = new LinkedHashMap<>();
        knnParameters.put("vector", vector);
        knnParameters.put("k", topK);
        if (!filters.isEmpty()) {
            knnParameters.put(
                    "filter",
                    filters.size() == 1
                            ? filters.get(0)
                            : Map.of("bool", Map.of("filter", filters))
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", topK);
        body.put("_source", List.of(
                "documentId",
                "id",
                "title",
                contentFieldName,
                "documentType",
                "resourceTypes",
                "sourcePath",
                "providerVersion",
                "corpusVersion",
                "authority",
                "priority",
                "riskTags"
        ));
        body.put("query", Map.of(
                "knn", Map.of(
                        vectorFieldName, knnParameters
                )
        ));

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to build OpenSearch k-NN query", exception);
        }
    }

    private List<String> normalize(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::strip)
                        .distinct()
                        .toList();
    }

    /** One actual official document for one exact metadata type; no vector/model request. */
    public String buildOfficialDocumentation(String contentFieldName, String corpusVersion,
            String providerVersion, String resourceType) {
        if (contentFieldName == null || contentFieldName.isBlank()
                || corpusVersion == null || corpusVersion.isBlank()
                || providerVersion == null || providerVersion.isBlank()
                || resourceType == null || !resourceType.matches("aws_[a-z0-9_]+")) {
            throw new IllegalArgumentException("official documentation lookup requires exact resource and version metadata");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", 1);
        body.put("_source", List.of("documentId", "id", "title", contentFieldName, "documentType",
                "resourceTypes", "sourcePath", "providerVersion", "corpusVersion", "authority", "priority", "riskTags"));
        body.put("query", Map.of("bool", Map.of("filter", List.of(
                Map.of("term", Map.of("corpusVersion", corpusVersion)),
                Map.of("term", Map.of("providerVersion", providerVersion)),
                Map.of("term", Map.of("resourceTypes", resourceType)),
                Map.of("term", Map.of("authority", "PROVIDER_DOCUMENTATION")),
                Map.of("terms", Map.of("documentType", List.of("AWS_PROVIDER_DOC", "AWS_PROVIDER_EXAMPLE")))))));
        body.put("sort", List.of(Map.of("priority", "desc"), Map.of("documentId", "asc")));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to build official documentation lookup", exception);
        }
    }

    private void addTermFilter(List<Map<String, Object>> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Map.of("term", Map.of(field, value.strip())));
        }
    }
}
