package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisGenerationOutputTruncatedException;
import com.terraformers.modernization.analysis.AnalysisGenerationResponseFormatException;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisProviderTimeoutException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.RequiredGroundingPolicy;
import com.terraformers.modernization.analysis.TerraformDraftValidation;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.analysis.vertex.VertexGroundedGenerationOrchestrator;
import com.terraformers.modernization.analysis.vertex.VertexGroundedGenerationOrchestrator.Outcome;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationCase;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import com.terraformers.modernization.evaluation.EvaluationTrace.FactExtractionEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.FirstDivergence;
import com.terraformers.modernization.evaluation.EvaluationTrace.GenerationEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.GroundingClosureEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.ClosureRetrievalEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.InputIdentity;
import com.terraformers.modernization.evaluation.EvaluationTrace.ReferenceHit;
import com.terraformers.modernization.evaluation.EvaluationTrace.RetrievalEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.StageTrace;
import com.terraformers.modernization.evaluation.EvaluationTrace.UsageEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationCheck;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationEvidence;
import com.terraformers.modernization.reference.ArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ArchitectureFactsExtractionException;
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
import java.util.concurrent.atomic.AtomicReference;
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
    private final EvaluationGenerationStage generationStage;
    private final VertexGroundedGenerationOrchestrator groundedGeneration;
    private final EvaluationTerraformValidator terraformValidator;
    private final String terraformValidatorName;
    private final RetrievalMode retrievalMode;
    private final ConfigurationIdentity configuration;
    private int initialEvidenceBudget;

    public EvaluationRunner(ArchitectureFactsExtractor factsExtractor, RetrievalQueryTextBuilder queryTextBuilder,
            ReferenceRetriever referenceRetriever, AnalysisGenerationStage generationStage,
            TerraformDraftValidator terraformDraftValidator, RetrievalMode retrievalMode,
            ConfigurationIdentity configuration) {
        this(factsExtractor, queryTextBuilder, referenceRetriever,
                (context, source, facts, references) -> generationStage.generate(context, source, references),
                terraformDraftValidator::validate, TerraformDraftValidator.class.getSimpleName(),
                retrievalMode, configuration);
    }

    public EvaluationRunner(ArchitectureFactsExtractor factsExtractor, RetrievalQueryTextBuilder queryTextBuilder,
            ReferenceRetriever referenceRetriever, EvaluationGenerationStage generationStage,
            EvaluationTerraformValidator terraformValidator, String terraformValidatorName,
            RetrievalMode retrievalMode, ConfigurationIdentity configuration) {
        this(factsExtractor, queryTextBuilder, referenceRetriever, generationStage, null,
                terraformValidator, terraformValidatorName, retrievalMode, configuration);
    }

    public EvaluationRunner(ArchitectureFactsExtractor factsExtractor, RetrievalQueryTextBuilder queryTextBuilder,
            ReferenceRetriever referenceRetriever, VertexGroundedGenerationOrchestrator groundedGeneration,
            EvaluationTerraformValidator terraformValidator, String terraformValidatorName,
            RetrievalMode retrievalMode, ConfigurationIdentity configuration) {
        this(factsExtractor, queryTextBuilder, referenceRetriever, null,
                Objects.requireNonNull(groundedGeneration, "groundedGeneration"),
                terraformValidator, terraformValidatorName, retrievalMode, configuration);
    }

    public EvaluationRunner(ArchitectureFactsExtractor factsExtractor, RetrievalQueryTextBuilder queryTextBuilder,
            ReferenceRetriever referenceRetriever, VertexGroundedGenerationOrchestrator groundedGeneration,
            EvaluationTerraformValidator terraformValidator, String terraformValidatorName,
            RetrievalMode retrievalMode, ConfigurationIdentity configuration, int initialEvidenceBudget) {
        this(factsExtractor, queryTextBuilder, referenceRetriever, groundedGeneration, terraformValidator,
                terraformValidatorName, retrievalMode, configuration);
        if (initialEvidenceBudget <= 0) throw new IllegalArgumentException("initial evidence budget must be positive");
        this.initialEvidenceBudget = initialEvidenceBudget;
    }

    private EvaluationRunner(ArchitectureFactsExtractor factsExtractor, RetrievalQueryTextBuilder queryTextBuilder,
            ReferenceRetriever referenceRetriever, EvaluationGenerationStage generationStage,
            VertexGroundedGenerationOrchestrator groundedGeneration, EvaluationTerraformValidator terraformValidator,
            String terraformValidatorName, RetrievalMode retrievalMode, ConfigurationIdentity configuration) {
        this.factsExtractor = Objects.requireNonNull(factsExtractor, "factsExtractor");
        this.queryTextBuilder = Objects.requireNonNull(queryTextBuilder, "queryTextBuilder");
        this.referenceRetriever = Objects.requireNonNull(referenceRetriever, "referenceRetriever");
        this.generationStage = groundedGeneration == null
                ? Objects.requireNonNull(generationStage, "generationStage") : null;
        this.groundedGeneration = groundedGeneration;
        this.terraformValidator = Objects.requireNonNull(terraformValidator, "terraformValidator");
        this.terraformValidatorName = requireText(terraformValidatorName, "terraformValidatorName");
        this.retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.initialEvidenceBudget = configuration.topK() == null ? 0 : configuration.topK();
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
        ArchitectureRetrievalFacts facts = new ArchitectureRetrievalFacts("", List.of(), List.of(), List.of());
        List<ReferenceDocument> references = List.of();

        if (retrievalMode != RetrievalMode.DISABLED) {
            long factsStartedAt = System.nanoTime();
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
                                factExtractionFailureCategory(exception),
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
                        loadedDataset, definition, runId, input, source, context, facts,
                        factTrace, StageTrace.notRun(EvaluationStage.RETRIEVAL), List.of()
                );
            }

            ReferenceQuery query;
            try {
                query = new ReferenceQuery(
                        queryTextBuilder.build(facts),
                        facts.resourceTypes(),
                        initialEvidenceBudget
                );
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
                        loadedDataset, definition, runId, input, source, context, facts,
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
                facts,
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
            ArchitectureRetrievalFacts facts,
            StageTrace<FactExtractionEvidence> factTrace,
            StageTrace<RetrievalEvidence> retrievalTrace,
            List<ReferenceDocument> references
    ) {
        long generationStartedAt = System.nanoTime();
        StageTrace<GenerationEvidence> generationTrace;
        AtomicReference<Outcome> groundingOutcome = new AtomicReference<>();
        try {
            AnalysisGenerationResult generated = groundedGeneration == null
                    ? generationStage.generate(context, source, facts, references)
                    : groundedGeneration.generate(context, source, facts, references, groundingOutcome::set).firstGeneration();
            EvaluationCase.InputClassification observed = classification(generated.inputClassification().name());
            GenerationEvidence evidence = generationEvidence(generated, references, observed, groundingOutcome.get());
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
            if (RequiredGroundingPolicy.isMissing(retrievalMode, generated.inputClassification(), references)) {
                generationTrace = StageTrace.fail(
                        EvaluationStage.GENERATION,
                        elapsedMillis(generationStartedAt),
                        evidence,
                        new EvaluationFailure(
                                EvaluationStage.GENERATION,
                                EvaluationFailureCategory.RETRIEVAL_EMPTY,
                                "required grounding is missing for architecture analysis"
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
                    exception.retryOccurred(),
                    groundedGeneration == null ? null : groundingEvidence(null, references)
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
            Outcome outcome = groundingOutcome.get();
            GenerationEvidence partialEvidence = outcome == null ? null : generationEvidence(
                    outcome.firstGeneration(), references,
                    classification(outcome.firstGeneration().inputClassification().name()), outcome);
            EvaluationFailureCategory category = outcome != null && RequiredGroundingPolicy.isMissing(
                    retrievalMode, outcome.firstGeneration().inputClassification(), references)
                    ? EvaluationFailureCategory.RETRIEVAL_EMPTY : generationFailureCategory(exception);
            generationTrace = StageTrace.fail(
                    EvaluationStage.GENERATION,
                    elapsedMillis(generationStartedAt),
                    partialEvidence,
                    failure(
                            EvaluationStage.GENERATION,
                            category,
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
        TerraformDraftValidation validation = terraformValidator.validate(
                generationTrace.evidence().terraformCode());
        ValidationEvidence evidence = new ValidationEvidence(
                new ValidationCheck(
                        terraformValidatorName,
                        validation.valid(),
                        validation.reason(),
                        validation.diagnosticSummary()
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
                            validationFailureCategory(validation),
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

    static EvaluationFailureCategory validationFailureCategory(TerraformDraftValidation validation) {
        String reason = validation == null || validation.reason() == null ? "" : validation.reason();
        if (reason.startsWith("AWS_PROVIDER_CONTRACT:")) {
            return EvaluationFailureCategory.TERRAFORM_PROVIDER_CONTRACT;
        }
        if (reason.startsWith("INIT_") || reason.startsWith("PROVIDER_CLOSURE:")) {
            return EvaluationFailureCategory.TERRAFORM_INIT_CONFIGURATION;
        }
        if (reason.startsWith("VALIDATE_")) {
            return EvaluationFailureCategory.TERRAFORM_EXECUTABLE_VALIDATION;
        }
        return EvaluationFailureCategory.TERRAFORM_STRUCTURAL_VALIDATION;
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
        return new RetrievalEvidence(query.text(), query.resourceTypes(), query.limit(), referenceHits(references));
    }

    private List<ReferenceHit> referenceHits(List<ReferenceDocument> references) {
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
        return List.copyOf(hits);
    }

    private GenerationEvidence generationEvidence(
            AnalysisGenerationResult generated,
            List<ReferenceDocument> references,
            EvaluationCase.InputClassification observed,
            Outcome outcome
    ) {
        UsageEvidence usage = generated.outputTokens() == null
                ? null
                : new UsageEvidence(null, generated.outputTokens(), null, "");
        String terraform = outcome == null ? generated.terraformCode() : outcome.finalTerraform();
        return new GenerationEvidence(
                references.stream().map(ReferenceDocument::id).toList(),
                observed,
                generated.classificationConfidence(),
                generated.summary(),
                generated.components(),
                generated.relationships(),
                generated.warnings(),
                terraform,
                matches(RESOURCE_TYPE, terraform),
                matches(MODULE_SOURCE, terraform),
                generated.stopReason(),
                usage,
                generated.retryOccurred(),
                outcome == null ? null : groundingEvidence(outcome, references)
        );
    }

    private GroundingClosureEvidence groundingEvidence(Outcome outcome, List<ReferenceDocument> initial) {
        if (outcome == null) {
            return new GroundingClosureEvidence("", false, null, referenceHits(initial), false, List.of());
        }
        var closure = outcome.closureRetrieval();
        ClosureRetrievalEvidence closureEvidence = closure == null ? null : new ClosureRetrievalEvidence(
                closure.query().text(), closure.query().resourceTypes(), closure.query().limit(),
                referenceHits(closure.references()));
        return new GroundingClosureEvidence(outcome.firstDraftTerraform(), outcome.closureAttempted(),
                closureEvidence, referenceHits(outcome.finalReferences()), outcome.repairAttempted(),
                outcome.finalGeneratedResourceEvidenceGaps());
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
        String detail = exception instanceof ArchitectureFactsExtractionException factsFailure
                ? factsFailure.evaluationDetail()
                : exception.getClass().getSimpleName();
        return new EvaluationFailure(stage, category, detail);
    }

    EvaluationFailureCategory generationFailureCategory(RuntimeException exception) {
        if (exception instanceof AnalysisProviderFailureException providerFailure) {
            return switch (providerFailure.reason()) {
                case OUTPUT_TRUNCATED -> EvaluationFailureCategory.OUTPUT_TRUNCATED;
                case CONTENT_BLOCKED -> EvaluationFailureCategory.PROVIDER_CONTENT_BLOCKED;
                case EMPTY_RESPONSE -> EvaluationFailureCategory.PROVIDER_EMPTY_RESPONSE;
                case RATE_LIMITED -> EvaluationFailureCategory.PROVIDER_RATE_LIMITED;
                case PROVIDER_ERROR -> EvaluationFailureCategory.PROVIDER_ERROR;
                case RESPONSE_FORMAT -> EvaluationFailureCategory.RESPONSE_FORMAT;
                case INPUT_REJECTED -> EvaluationFailureCategory.INPUT_CLASSIFICATION;
            };
        }
        if (exception instanceof AnalysisGenerationOutputTruncatedException) {
            return EvaluationFailureCategory.OUTPUT_TRUNCATED;
        }
        if (exception instanceof AnalysisGenerationResponseFormatException) {
            return EvaluationFailureCategory.RESPONSE_FORMAT;
        }
        if (exception instanceof AnalysisProviderTimeoutException) {
            return EvaluationFailureCategory.PROVIDER_TIMEOUT;
        }
        return EvaluationFailureCategory.PROVIDER_RUNTIME;
    }

    EvaluationFailureCategory factExtractionFailureCategory(RuntimeException exception) {
        if (exception instanceof ArchitectureFactsExtractionException factsFailure) {
            return switch (factsFailure.reason()) {
                case PROVIDER_RUNTIME -> EvaluationFailureCategory.PROVIDER_RUNTIME;
                case PROVIDER_CONTENT_BLOCKED -> EvaluationFailureCategory.PROVIDER_CONTENT_BLOCKED;
                case PROVIDER_TIMEOUT -> EvaluationFailureCategory.PROVIDER_TIMEOUT;
                case PROVIDER_RATE_LIMITED -> EvaluationFailureCategory.PROVIDER_RATE_LIMITED;
                case PROVIDER_ERROR -> EvaluationFailureCategory.PROVIDER_ERROR;
                case RESPONSE_TRUNCATED -> EvaluationFailureCategory.OUTPUT_TRUNCATED;
                case EMPTY_RESPONSE -> EvaluationFailureCategory.PROVIDER_EMPTY_RESPONSE;
                case INVALID_RESPONSE, EMPTY_FACTS -> EvaluationFailureCategory.RESPONSE_FORMAT;
            };
        }
        return EvaluationFailureCategory.PROVIDER_RUNTIME;
    }

    @FunctionalInterface
    public interface EvaluationGenerationStage {
        AnalysisGenerationResult generate(
                AnalysisRequestContext context,
                ObjectContent source,
                ArchitectureRetrievalFacts facts,
                List<ReferenceDocument> references
        );
    }

    @FunctionalInterface
    public interface EvaluationTerraformValidator {
        TerraformDraftValidation validate(String candidate);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
