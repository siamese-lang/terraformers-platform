package com.terraformers.modernization.evaluation;

import java.util.List;
import java.util.Objects;

public record EvaluationCase(
        String schemaVersion,
        String datasetVersion,
        String caseId,
        InputFixture input,
        InputClassification expectedClassification,
        TextExpectation components,
        TextExpectation relationships,
        TextExpectation resourceTypes,
        RetrievalExpectation retrieval,
        GenerationExpectation generation,
        ValidationExpectation validation,
        List<String> notes
) {
    public EvaluationCase {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        caseId = requireText(caseId, "caseId");
        input = Objects.requireNonNull(input, "input");
        expectedClassification = Objects.requireNonNull(expectedClassification, "expectedClassification");
        components = components == null ? TextExpectation.empty() : components;
        relationships = relationships == null ? TextExpectation.empty() : relationships;
        resourceTypes = resourceTypes == null ? TextExpectation.empty() : resourceTypes;
        retrieval = retrieval == null ? RetrievalExpectation.empty() : retrieval;
        generation = generation == null ? GenerationExpectation.empty() : generation;
        validation = validation == null ? ValidationExpectation.NOT_APPLICABLE : validation;
        notes = immutable(notes);
    }

    public enum InputClassification {
        ARCHITECTURE_DIAGRAM,
        AMBIGUOUS,
        NON_ARCHITECTURE_IMAGE
    }

    public enum ValidationExpectation {
        PASS,
        FAIL,
        NOT_APPLICABLE
    }

    public record InputFixture(
            String path,
            String sha256,
            String contentType
    ) {
        public InputFixture {
            path = requireText(path, "path");
            sha256 = requireText(sha256, "sha256");
            contentType = requireText(contentType, "contentType");
        }
    }

    public record TextExpectation(
            List<String> required,
            List<String> acceptable,
            List<String> forbidden
    ) {
        public TextExpectation {
            required = immutable(required);
            acceptable = immutable(acceptable);
            forbidden = immutable(forbidden);
        }

        public static TextExpectation empty() {
            return new TextExpectation(List.of(), List.of(), List.of());
        }
    }

    public record RetrievalExpectation(
            List<String> requiredReferenceIds,
            List<String> acceptableAuthorities,
            List<String> requiredResourceTypes,
            List<String> requiredProjectDecisionIds,
            List<String> forbiddenRiskTags
    ) {
        public RetrievalExpectation {
            requiredReferenceIds = immutable(requiredReferenceIds);
            acceptableAuthorities = immutable(acceptableAuthorities);
            requiredResourceTypes = immutable(requiredResourceTypes);
            requiredProjectDecisionIds = immutable(requiredProjectDecisionIds);
            forbiddenRiskTags = immutable(forbiddenRiskTags);
        }

        public static RetrievalExpectation empty() {
            return new RetrievalExpectation(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record GenerationExpectation(
            boolean terraformExpected,
            TextExpectation terraformResourceTypes
    ) {
        public GenerationExpectation {
            terraformResourceTypes = terraformResourceTypes == null
                    ? TextExpectation.empty()
                    : terraformResourceTypes;
        }

        public static GenerationExpectation empty() {
            return new GenerationExpectation(false, TextExpectation.empty());
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
