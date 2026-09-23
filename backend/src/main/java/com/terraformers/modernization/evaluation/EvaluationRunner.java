package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisProviderTimeoutException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.TerraformDraftValidation;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.analysis.bedrock.BedrockOutputTruncatedException;
import com.terraformers.modernization.analysis.bedrock.BedrockResponseFormatException;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationCase;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import com.terraformers.modernization.evaluation.EvaluationTrace.FactExtractionEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.FirstDivergence;
import com.terraformers.modernization.evaluation.EvaluationTrace.GenerationEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.InputIdentity;
import com.terraformers.modernization.evaluation.EvaluationTrace.ReferenceHit;
import com.terraformers.modernization.evaluation.EvaluationTrace.RetrievalEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.StageTrace;
import com.terraformers.modernization.evaluation.EvaluationTrace.UsageEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationCheck;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationEvidence;
import com.terraformers.modernization.reference.ArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EvaluationRunner {

    private static final Pattern RESOURCE_TYPE = Pattern.compile(
            "(?m)^\\s*resource\\s+\"([^\"]+)\"\\s+\"[^\"]+\"");
    private static final Pattern MODULE_SOURCE = Pattern.compile(
            "(?ms)^\\s*module\\s+\"[^\"]+\"\\s*\\{.*?^\\s*source\\s*=\\s*\"([^\"]+)\"");

    private final ArchitectureFactsExtractor factsExtractor;
    private final RetrievalQueryTextBuilder queryTextBuilder;
    private final ReferenceRetriever referenceRetriever;
    private final AnalysisGenerationStage generationStage;
    private final TerraformDraftValidator terraformDraftValidator;
    private final RetrievalMode retrievalMode;
    private final ConfigurationIdentity configuration;

    public EvaluationRunner(
            ArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder,
            ReferenceRetriever referenceRetriever,
            AnalysisGenerationStage generationStage,
            TerraformDraftValidator terraformDraftValidator,
            RetrievalMode retrievalMode,
            ConfigurationIdentity configuration
    ) {
        this.factsExtractor = Objects.requireNonNull(factsExtractor, "factsExtractor");
        this.queryTextBuilder = Objects.requireNonNull(queryTextBuilder, "queryTextBuilder");
        this.referenceRetriever = Objects.requireNonNull(referenceRetriever, "referenceRetriever");
        this.generationStage = Objects.requireNonNull(generationStage, "generationStage");
        this.terraformDraftValidator = Objects.requireNonNull(terraformDraftValidator, "terraformDraftValidator");
        this.retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if (!retrievalMode.name().equals(configuration.retrievalMode())) {
            throw new IllegalArgumentException("configuration retrievalMode must match runner retrievalMode");
        }
        if (retrievalMode != RetrievalMode.DISABLED && configuration.topK() == null) {
            throw new IllegalArgumentException("configuration topK is required when retrieval is enabled");
        }
    }

    public EvaluationRunResult run(LoadedEvaluationDataset loadedDataset, String runId) {
        Objects.requireNonNull(loadedDataset, "loadedDataset");
        List<EvaluationTrace> traces = loadedDataset.cases().stream()
                .map(loadedCase -> runCase(loadedDataset, loadedCase, runId))
                .toList();
        return new EvaluationRunResult(
                loadedDataset.dataset().schemaVersion(),
                loadedDataset.dataset().datasetVersion(),
                runId,
                configuration,
                traces
        );
    }

    private EvaluationTrace runCase(
            LoadedEvaluationDataset loadedDataset,
            LoadedEvaluationCase loadedCase,
            String runId
    ) {
        EvaluationCase definition = loadedCase.definition();
        InputIdentity input = new InputIdentity(
                definition.input().path(),
                definition.input().sha256(),
                definition.input().contentType()
        );
        byte[] inputBytes = loadedCase.inputBytes();
        ObjectContent source = new ObjectContent(
                new ObjectMetadata(
                        "evaluation",
                        definition.caseId(),
                        definition.input().contentType(),
                        inputBytes.length,
                        definition.input().sha256()
                ),
                inputBytes
        );
        AnalysisRequestContext context = new AnalysisRequestContext(
                runId + ":" + definition.caseId(),
                "evaluation",
                "evaluation",
                definition.caseId(),
                runId,
                AnalysisMode.INTEGRATED_JAVA
        );

        StageTrace<FactExtractionEvidence> factTrace = StageTrace.notRun(EvaluationStage.FACT_EXTRACTION);
        StageTrace<RetrievalEvidence> retrievalTrace = StageTrace.notRun(EvaluationStage.RETRIEVAL);
        List<ReferenceDocument> references = List.of();

        if (retrievalMode != RetrievalMode.DISABLED) {
            long factsStartedAt = System.nanoTime();
            ArchitectureRetrievalFacts facts;
            try {
                facts = factsExtractor.extract(source);
                factTrace = StageTrace.pass(
                        EvaluationStage.FACT_EXTRACTION,
                        elapsedMillis(factsStartedAt),
                        new FactExtractionEvidence(
                                null,
                                facts.summary(),
                                facts.components(),
                                facts.relationships(),
                                facts.resourceTypes()
                        )
                );
            } catch (RuntimeException exception) {
                factTrace = StageTrace.fail(
                        EvaluationStage.FACT_EXTRACTION,
                        elapsedMillis(factsStartedAt),
                        null,
                        failure(
                                EvaluationStage.FACT_EXTRACTION,
                                EvaluationFailureCategory.PROVIDER_RUNTIME,
                                exception
                        )
                );
                if (retrievalMode == RetrievalMode.REQUIRED) {
                    return trace(
                            loadedDataset,
                            definition,
                            runId,
                            input,
                            factTrace,
                            StageTrace.notRun(EvaluationStage.RETRIEVAL),
                            StageTrace.notRun(EvaluationStage.GENERATION),
                            StageTrace.notRun(EvaluationStage.VALIDATION)
                    );
                }
                return continueGeneration(
                        loadedDataset, definition, runId, input, source, context,
                        factTrace, StageTrace.notRun(EvaluationStage.RETRIEVAL), List.of()
                );
            }

            ReferenceQuery query;
            try {
                query = new ReferenceQuery(queryTextBuilder.build(facts), configuration.topK());
            } catch (RuntimeException exception) {
                retrievalTrace = StageTrace.fail(
                        EvaluationStage.RETRIEVAL,
                        0,
                        null,
                        failure(
                                EvaluationStage.RETRIEVAL,
                                EvaluationFailureCategory.RETRIEVAL_QUERY_CONSTRUCTION,
                                exception
                        )
                );
                if (retrievalMode == RetrievalMode.REQUIRED) {
                    return trace(
                            loadedDataset,
                            definition,
                            runId,
                            input,
                            factTrace,
                            retrievalTrace,
                            StageTrace.notRun(EvaluationStage.GENERATION),
                            StageTrace.notRun(EvaluationStage.VALIDATION)
                    );
                }
                return continueGeneration(
                        loadedDataset, definition, runId, input, source, context,
                        factTrace, retrievalTrace, List.of()
                );
            }

            long retrievalStartedAt = System.nanoTime();
            try {
                references = referenceRetriever.retrieve(query);
                retrievalTrace = StageTrace.pass(
                        EvaluationStage.RETRIEVAL,
                        elapsedMillis(retrievalStartedAt),
                        retrievalEvidence(query, references)
                );
            } catch (RuntimeException exception) {
                retrievalTrace = StageTrace.fail(
                        EvaluationStage.RETRIEVAL,
                        elapsedMillis(retrievalStartedAt),
                        new RetrievalEvidence(
                                query.text(),
                                query.resourceTypes(),
                                query.limit(),
                                List.of()
                        ),
                        failure(
                                EvaluationStage.RETRIEVAL,
                                EvaluationFailureCategory.RETRIEVAL_FAILURE,
                                exception
                        )
                );
                if (retrievalMode == RetrievalMode.REQUIRED) {
                    return trace(
                            loadedDataset,
                            definition,
                            runId,
                            input,
                            factTrace,
                            retrievalTrace,
                            StageTrace.notRun(EvaluationStage.GENERATION),
                            StageTrace.notRun(EvaluationStage.VALIDATION)
                    );
                }
                references = List.of();
            }
        }

        return continueGeneration(
                loadedDataset,
                definition,
                runId,
                input,
                source,
                context,
                factTrace,
                retrievalTrace,
                references
        );
    }

    private EvaluationTrace continueGeneration(
            LoadedEvaluationDataset loadedDataset,
            EvaluationCase definition,
            String runId,
            InputIdentity input,
            ObjectContent source,
            AnalysisRequestContext context,
            StageTrace<FactExtractionEvidence> factTrace,
            StageTrace<RetrievalEvidence> retrievalTrace,
            List<ReferenceDocument> references
    ) {
        long generationStartedAt = System.nanoTime();
        StageTrace<GenerationEvidence> generationTrace;
        try {
            AnalysisGenerationResult generated = generationStage.generate(context, source, references);
            EvaluationCase.InputClassification observed = classification(generated.inputClassification().name());
            GenerationEvidence evidence = generationEvidence(generated, references, observed);
            if (observed != definition.expectedClassification()) {
                generationTrace = StageTrace.fail(
                        EvaluationStage.GENERATION,
                        elapsedMillis(generationStartedAt),
                        evidence,
                        new EvaluationFailure(
                                EvaluationStage.GENERATION,
                                EvaluationFailureCategory.INPUT_CLASSIFICATION,
                                "observed classification " + observed + " but expected "
                                        + definition.expectedClassification()
                        )
                );
                return trace(
                        loadedDataset,
                        definition,
                        runId,
                        input,
                        factTrace,
                        retrievalTrace,
                        generationTrace,
                        StageTrace.notRun(EvaluationStage.VALIDATION)
                );
            }
            generationTrace = StageTrace.pass(
                    EvaluationStage.GENERATION,
                    elapsedMillis(generationStartedAt),
                    evidence
            );
        } catch (AnalysisInputRejectedException exception) {
            EvaluationCase.InputClassification observed = classification(exception.classification().name());
            GenerationEvidence evidence = new GenerationEvidence(
                    references.stream().map(ReferenceDocument::id).toList(),
                    observed,
                    exception.classificationConfidence(),
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    "",
                    List.of(),
                    List.of(),
                    "",
                    null,
                    exception.retryOccurred()
            );
            if (observed == definition.expectedClassification()) {
                generationTrace = StageTrace.pass(
                        EvaluationStage.GENERATION,
                        elapsedMillis(generationStartedAt),
                        evidence
                );
            } else {
                generationTrace = StageTrace.fail(
                        EvaluationStage.GENERATION,
                        elapsedMillis(generationStartedAt),
                        evidence,
                        new EvaluationFailure(
                                EvaluationStage.GENERATION,
                                EvaluationFailureCategory.INPUT_CLASSIFICATION,
                                "observed classification " + observed + " but expected "
                                        + definition.expectedClassification()
                        )
                );
            }
            return trace(
                    loadedDataset,
                    definition,
                    runId,
                    input,
                    factTrace,
                    retrievalTrace,
                    generationTrace,
                    StageTrace.notRun(EvaluationStage.VALIDATION)
            );
        } catch (RuntimeException exception) {
            generationTrace = StageTrace.fail(
                    EvaluationStage.GENERATION,
                    elapsedMillis(generationStartedAt),
                    null,
                    failure(
                            EvaluationStage.GENERATION,
                            generationFailureCategory(exception),
                            exception
                    )
            );
            return trace(
                    loadedDataset,
                    definition,
                    runId,
                    input,
                    factTrace,
                    retrievalTrace,
                    generationTrace,
                    StageTrace.notRun(EvaluationStage.VALIDATION)
            );
        }

        if (definition.expectedClassification() != EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM) {
            return trace(
                    loadedDataset,
                    definition,
                    runId,
                    input,
                    factTrace,
                    retrievalTrace,
                    generationTrace,
                    StageTrace.notRun(EvaluationStage.VALIDATION)
            );
        }

        long validationStartedAt = System.nanoTime();
        TerraformDraftValidation validation = terraformDraftValidator.validate(
                generationTrace.evidence().terraformCode());
        ValidationEvidence evidence = new ValidationEvidence(
                new ValidationCheck(
                        "TerraformDraftValidator",
                        validation.valid(),
                        validation.reason()
                ),
                List.of()
        );
        StageTrace<ValidationEvidence> validationTrace;
        if (validation.valid()) {
            validationTrace = StageTrace.pass(
                    EvaluationStage.VALIDATION,
                    elapsedMillis(validationStartedAt),
                    evidence
            );
        } else {
            validationTrace = StageTrace.fail(
                    EvaluationStage.VALIDATION,
                    elapsedMillis(validationStartedAt),
                    evidence,
                    new EvaluationFailure(
                            EvaluationStage.VALIDATION,
                            EvaluationFailureCategory.TERRAFORM_STRUCTURAL_VALIDATION,
                            validation.reason()
                    )
            );
        }
        return trace(
                loadedDataset,
                definition,
                runId,
                input,
                factTrace,
                retrievalTrace,
                generationTrace,
                validationTrace
        );
    }

    private EvaluationTrace trace(
            LoadedEvaluationDataset loadedDataset,
            EvaluationCase definition,
            String runId,
            InputIdentity input,
            StageTrace<FactExtractionEvidence> factTrace,
            StageTrace<RetrievalEvidence> retrievalTrace,
            StageTrace<GenerationEvidence> generationTrace,
            StageTrace<ValidationEvidence> validationTrace
    ) {
        return new EvaluationTrace(
                loadedDataset.dataset().schemaVersion(),
                loadedDataset.dataset().datasetVersion(),
                runId,
                definition.caseId(),
                input,
                configuration,
                factTrace,
                retrievalTrace,
                generationTrace,
                validationTrace,
                firstDivergence(factTrace, retrievalTrace, generationTrace, validationTrace)
        );
    }

    private FirstDivergence firstDivergence(StageTrace<?>... traces) {
        for (StageTrace<?> trace : traces) {
            if (trace.status() == EvaluationStageStatus.FAIL) {
                EvaluationFailure failure = trace.failures().get(0);
                return new FirstDivergence(trace.stage(), failure.category());
            }
        }
        return null;
    }

    private RetrievalEvidence retrievalEvidence(ReferenceQuery query, List<ReferenceDocument> references) {
        List<ReferenceHit> hits = new ArrayList<>();
        for (int index = 0; index < references.size(); index++) {
            ReferenceDocument reference = references.get(index);
            hits.add(new ReferenceHit(
                    index + 1,
                    reference.id(),
                    reference.score(),
                    reference.title(),
                    reference.authority(),
                    reference.documentType(),
                    reference.sourcePath(),
                    reference.resourceTypes(),
                    reference.providerVersion(),
                    reference.corpusVersion(),
                    reference.priority(),
                    reference.riskTags()
            ));
        }
        return new RetrievalEvidence(query.text(), query.resourceTypes(), query.limit(), hits);
    }

    private GenerationEvidence generationEvidence(
            AnalysisGenerationResult generated,
            List<ReferenceDocument> references,
            EvaluationCase.InputClassification observed
    ) {
        UsageEvidence usage = generated.outputTokens() == null
                ? null
                : new UsageEvidence(null, generated.outputTokens(), null, "");
        return new GenerationEvidence(
                references.stream().map(ReferenceDocument::id).toList(),
                observed,
                generated.classificationConfidence(),
                generated.summary(),
                generated.components(),
                generated.relationships(),
                generated.warnings(),
                generated.terraformCode(),
                matches(RESOURCE_TYPE, generated.terraformCode()),
                matches(MODULE_SOURCE, generated.terraformCode()),
                generated.stopReason(),
                usage,
                generated.retryOccurred()
        );
    }

    private List<String> matches(Pattern pattern, String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Matcher matcher = pattern.matcher(value);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            String matched = matcher.group(1);
            if (!values.contains(matched)) {
                values.add(matched);
            }
        }
        return List.copyOf(values);
    }

    private EvaluationCase.InputClassification classification(String value) {
        return EvaluationCase.InputClassification.valueOf(value);
    }

    private EvaluationFailure failure(
            EvaluationStage stage,
            EvaluationFailureCategory category,
            RuntimeException exception
    ) {
        return new EvaluationFailure(stage, category, exception.getClass().getSimpleName());
    }

    private EvaluationFailureCategory generationFailureCategory(RuntimeException exception) {
        if (exception instanceof BedrockOutputTruncatedException) {
            return EvaluationFailureCategory.OUTPUT_TRUNCATED;
        }
        if (exception instanceof BedrockResponseFormatException) {
            return EvaluationFailureCategory.RESPONSE_FORMAT;
        }
        if (exception instanceof AnalysisProviderTimeoutException) {
            return EvaluationFailureCategory.PROVIDER_TIMEOUT;
        }
        return EvaluationFailureCategory.PROVIDER_RUNTIME;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
