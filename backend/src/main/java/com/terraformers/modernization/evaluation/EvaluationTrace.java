package com.terraformers.modernization.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record EvaluationTrace(
        String schemaVersion,
        String datasetVersion,
        String runId,
        String caseId,
        InputIdentity input,
        ConfigurationIdentity configuration,
        StageTrace<FactExtractionEvidence> factExtraction,
        StageTrace<RetrievalEvidence> retrieval,
        StageTrace<GenerationEvidence> generation,
        StageTrace<ValidationEvidence> validation,
        FirstDivergence firstDivergence
) {
    public EvaluationTrace {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        datasetVersion = requireText(datasetVersion, "datasetVersion");
        runId = requireText(runId, "runId");
        caseId = requireText(caseId, "caseId");
        input = Objects.requireNonNull(input, "input");
        configuration = Objects.requireNonNull(configuration, "configuration");
        factExtraction = requireStage(factExtraction, EvaluationStage.FACT_EXTRACTION, "factExtraction");
        retrieval = requireStage(retrieval, EvaluationStage.RETRIEVAL, "retrieval");
        generation = requireStage(generation, EvaluationStage.GENERATION, "generation");
        validation = requireStage(validation, EvaluationStage.VALIDATION, "validation");

        EvaluationStage earliestFailedStage = firstFailedStage(factExtraction, retrieval, generation, validation);
        if (earliestFailedStage == null && firstDivergence != null) {
            throw new IllegalArgumentException("firstDivergence must be null when no stage failed");
        }
        if (earliestFailedStage != null) {
            if (firstDivergence == null) {
                throw new IllegalArgumentException("firstDivergence is required when a stage failed");
            }
            if (firstDivergence.stage() != earliestFailedStage) {
                throw new IllegalArgumentException("firstDivergence must identify the earliest failed stage");
            }
            StageTrace<?> earliestTrace = traceFor(
                    earliestFailedStage, factExtraction, retrieval, generation, validation);
            boolean categoryPresent = earliestTrace.failures().stream()
                    .anyMatch(failure -> failure.category() == firstDivergence.category());
            if (!categoryPresent) {
                throw new IllegalArgumentException(
                        "firstDivergence category must be present in the earliest failed stage");
            }
        }
    }

    public record InputIdentity(
            String fixturePath,
            String sha256,
            String contentType
    ) {
        public InputIdentity {
            fixturePath = requireText(fixturePath, "fixturePath");
            sha256 = requireText(sha256, "sha256");
            contentType = requireText(contentType, "contentType");
        }
    }

    public record ConfigurationIdentity(
            String corpusVersion,
            String providerVersion,
            String analysisProvider,
            String embeddingProvider,
            String retrievalMode,
            Integer topK,
            String generationModelId,
            String embeddingModelId,
            String configurationFingerprint
    ) {
        public ConfigurationIdentity {
            corpusVersion = normalize(corpusVersion);
            providerVersion = normalize(providerVersion);
            analysisProvider = requireText(analysisProvider, "analysisProvider");
            embeddingProvider = requireText(embeddingProvider, "embeddingProvider");
            retrievalMode = requireText(retrievalMode, "retrievalMode");
            if (topK != null && topK <= 0) {
                throw new IllegalArgumentException("topK must be positive when set");
            }
            generationModelId = normalize(generationModelId);
            embeddingModelId = normalize(embeddingModelId);
            configurationFingerprint = requireText(configurationFingerprint, "configurationFingerprint");
        }
    }

    public record StageTrace<T>(
            EvaluationStage stage,
            EvaluationStageStatus status,
            Long latencyMs,
            T evidence,
            List<EvaluationFailure> failures
    ) {
        public StageTrace {
            stage = Objects.requireNonNull(stage, "stage");
            status = Objects.requireNonNull(status, "status");
            if (latencyMs != null && latencyMs < 0) {
                throw new IllegalArgumentException("latencyMs must not be negative");
            }
            failures = failures == null ? List.of() : List.copyOf(failures);
            if (status == EvaluationStageStatus.FAIL && failures.isEmpty()) {
                throw new IllegalArgumentException("failed stage must contain at least one failure");
            }
            if (status == EvaluationStageStatus.PASS && !failures.isEmpty()) {
                throw new IllegalArgumentException("passed stage must not contain failures");
            }
            if (failures.stream().anyMatch(failure -> failure.stage() != stage)) {
                throw new IllegalArgumentException("failure stage must match StageTrace stage");
            }
        }

        public static <T> StageTrace<T> pass(EvaluationStage stage, long latencyMs, T evidence) {
            return new StageTrace<>(stage, EvaluationStageStatus.PASS, latencyMs, evidence, List.of());
        }

        public static <T> StageTrace<T> fail(
                EvaluationStage stage,
                long latencyMs,
                T evidence,
                EvaluationFailure failure
        ) {
            return new StageTrace<>(stage, EvaluationStageStatus.FAIL, latencyMs, evidence, List.of(failure));
        }

        public static <T> StageTrace<T> notRun(EvaluationStage stage) {
            return new StageTrace<>(stage, EvaluationStageStatus.NOT_RUN, null, null, List.of());
        }

        public static <T> StageTrace<T> blocked(EvaluationStage stage, EvaluationFailure failure) {
            return new StageTrace<>(
                    stage,
                    EvaluationStageStatus.BLOCKED,
                    null,
                    null,
                    failure == null ? List.of() : List.of(failure)
            );
        }
    }

    public record FactExtractionEvidence(
            EvaluationCase.InputClassification classification,
            String summary,
            List<String> components,
            List<String> relationships,
            List<String> resourceTypes
    ) {
        public FactExtractionEvidence {
            classification = Objects.requireNonNull(classification, "classification");
            summary = normalize(summary);
            components = immutable(components);
            relationships = immutable(relationships);
            resourceTypes = immutable(resourceTypes);
        }
    }

    public record RetrievalEvidence(
            String queryText,
            List<String> resourceTypeFilters,
            int requestedTopK,
            List<ReferenceHit> hits
    ) {
        public RetrievalEvidence {
            queryText = requireText(queryText, "queryText");
            resourceTypeFilters = immutable(resourceTypeFilters);
            if (requestedTopK <= 0) {
                throw new IllegalArgumentException("requestedTopK must be positive");
            }
            hits = hits == null ? List.of() : List.copyOf(hits);
        }
    }

    public record ReferenceHit(
            int rank,
            String documentId,
            double score,
            String title,
            String authority,
            String documentType,
            String sourcePath,
            List<String> resourceTypes,
            String providerVersion,
            String corpusVersion,
            int priority,
            List<String> riskTags
    ) {
        public ReferenceHit {
            if (rank <= 0) {
                throw new IllegalArgumentException("rank must be positive");
            }
            documentId = requireText(documentId, "documentId");
            title = normalize(title);
            authority = normalize(authority);
            documentType = normalize(documentType);
            sourcePath = normalize(sourcePath);
            resourceTypes = immutable(resourceTypes);
            providerVersion = normalize(providerVersion);
            corpusVersion = normalize(corpusVersion);
            riskTags = immutable(riskTags);
        }
    }

    public record GenerationEvidence(
            List<String> suppliedReferenceIds,
            String summary,
            List<String> components,
            List<String> relationships,
            List<String> warnings,
            String terraformCode,
            List<String> generatedResourceTypes,
            List<String> generatedModuleSources,
            String stopReason,
            UsageEvidence usage,
            boolean retryOccurred
    ) {
        public GenerationEvidence {
            suppliedReferenceIds = immutable(suppliedReferenceIds);
            summary = normalize(summary);
            components = immutable(components);
            relationships = immutable(relationships);
            warnings = immutable(warnings);
            terraformCode = terraformCode == null ? "" : terraformCode;
            generatedResourceTypes = immutable(generatedResourceTypes);
            generatedModuleSources = immutable(generatedModuleSources);
            stopReason = normalize(stopReason);
        }
    }

    public record UsageEvidence(
            Integer inputTokens,
            Integer outputTokens,
            BigDecimal cost,
            String currency
    ) {
        public UsageEvidence {
            if (inputTokens != null && inputTokens < 0) {
                throw new IllegalArgumentException("inputTokens must not be negative");
            }
            if (outputTokens != null && outputTokens < 0) {
                throw new IllegalArgumentException("outputTokens must not be negative");
            }
            if (cost != null && cost.signum() < 0) {
                throw new IllegalArgumentException("cost must not be negative");
            }
            currency = normalize(currency);
        }
    }

    public record ValidationEvidence(
            ValidationCheck applicationValidator,
            List<ValidationCheck> additionalChecks
    ) {
        public ValidationEvidence {
            applicationValidator = Objects.requireNonNull(applicationValidator, "applicationValidator");
            additionalChecks = additionalChecks == null ? List.of() : List.copyOf(additionalChecks);
        }
    }

    public record ValidationCheck(
            String name,
            boolean valid,
            String reason
    ) {
        public ValidationCheck {
            name = requireText(name, "name");
            reason = normalize(reason);
        }
    }

    public record FirstDivergence(
            EvaluationStage stage,
            EvaluationFailureCategory category
    ) {
        public FirstDivergence {
            stage = Objects.requireNonNull(stage, "stage");
            category = Objects.requireNonNull(category, "category");
        }
    }

    private static EvaluationStage firstFailedStage(StageTrace<?>... traces) {
        for (StageTrace<?> trace : traces) {
            if (trace.status() == EvaluationStageStatus.FAIL) {
                return trace.stage();
            }
        }
        return null;
    }

    private static StageTrace<?> traceFor(
            EvaluationStage stage,
            StageTrace<?> factExtraction,
            StageTrace<?> retrieval,
            StageTrace<?> generation,
            StageTrace<?> validation
    ) {
        return switch (stage) {
            case FACT_EXTRACTION -> factExtraction;
            case RETRIEVAL -> retrieval;
            case GENERATION -> generation;
            case VALIDATION -> validation;
        };
    }

    private static <T> StageTrace<T> requireStage(StageTrace<T> trace, EvaluationStage expected, String field) {
        Objects.requireNonNull(trace, field);
        if (trace.stage() != expected) {
            throw new IllegalArgumentException(field + " must use stage " + expected);
        }
        return trace;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
