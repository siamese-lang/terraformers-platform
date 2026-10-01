package com.terraformers.modernization.evaluation.adaptive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Pattern;

public record AdaptiveRetrievalProbeFixture(
        String schemaVersion,
        String scenarioId,
        String corpusVersion,
        String providerVersion,
        int minimumCorpusDocumentCover,
        ArchitectureRetrievalFacts facts
) {
    private static final String SCHEMA = "adaptive-retrieval-probe-fixture-v1";
    private static final Pattern RESOURCE_TYPE = Pattern.compile("aws_[a-z0-9_]+");

    public AdaptiveRetrievalProbeFixture {
        if (!SCHEMA.equals(schemaVersion)) {
            throw new IllegalArgumentException("unexpected adaptive retrieval fixture schema");
        }
        requireText(scenarioId, "scenarioId");
        requireText(corpusVersion, "corpusVersion");
        requireText(providerVersion, "providerVersion");
        if (facts == null || facts.isEmpty()) {
            throw new IllegalArgumentException("facts must not be empty");
        }
        if (facts.components().size() > 8 || facts.relationships().size() > 8) {
            throw new IllegalArgumentException("components and relationships must remain within the eight-item fact bound");
        }
        if (facts.resourceTypes().size() <= 8 || facts.resourceTypes().size() > ReferenceQuery.MAX_RESOURCE_TYPES) {
            throw new IllegalArgumentException("resourceTypes must exercise the >8 and <=16 adaptive retrieval boundary");
        }
        if (new HashSet<>(facts.resourceTypes()).size() != facts.resourceTypes().size()) {
            throw new IllegalArgumentException("resourceTypes must be distinct");
        }
        if (facts.resourceTypes().stream().anyMatch(value -> !RESOURCE_TYPE.matcher(value).matches())) {
            throw new IllegalArgumentException("resourceTypes must be canonical Terraform AWS resource types");
        }
        if (minimumCorpusDocumentCover <= 8) {
            throw new IllegalArgumentException("minimumCorpusDocumentCover must prove K8 is structurally insufficient");
        }
    }

    public static AdaptiveRetrievalProbeFixture load(ObjectMapper mapper, Path path) {
        try {
            return mapper.readValue(Files.readString(path), AdaptiveRetrievalProbeFixture.class);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load adaptive retrieval probe fixture", exception);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
