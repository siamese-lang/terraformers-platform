package com.terraformers.modernization.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.GeneratedTerraformContractInspector;
import com.terraformers.modernization.analysis.GeneratedTerraformContractViolation;
import com.terraformers.modernization.analysis.TerraformCliValidator;
import com.terraformers.modernization.analysis.TerraformDraftValidation;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.analysis.vertex.VertexGenerationStage;
import com.terraformers.modernization.analysis.vertex.VertexGroundedGenerationOrchestrator;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.analysis.vertex.VertexResponseParser;
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationCase;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import com.terraformers.modernization.reference.ArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.RetrievalModeReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import com.terraformers.modernization.reference.VertexEmbeddingProvider;
import com.terraformers.modernization.reference.opensearch.HttpOpenSearchTransport;
import com.terraformers.modernization.reference.opensearch.OpenSearchKnnQueryBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchReferenceRetriever;
import com.terraformers.modernization.reference.opensearch.OpenSearchResponseParser;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone entry point: it deliberately creates no Spring application context. */
public final class LiveEvaluationLauncher {
    private LiveEvaluationLauncher() {}

    public static void main(String[] args) {
        LiveEvaluationConfiguration configuration = parse(args, System.getenv());
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LoadedEvaluationDataset loaded = select(
                new EvaluationDatasetLoader(objectMapper).load(configuration.datasetFile()), configuration);
        int initialEvidenceBudget = initialEvidenceBudget(configuration, loaded, System.getenv());

        VertexRuntimeProperties vertex = vertexProperties(configuration);
        AnalysisRuntimeProperties analysis = analysisProperties(configuration);
        Client client = Client.builder()
                .project(vertex.requireProjectId()).location(vertex.requireLocation()).vertexAI(true)
                .httpOptions(HttpOptions.builder().apiVersion("v1").build()).build();
        OpenSearchReferenceRetriever vectorRetriever = new OpenSearchReferenceRetriever(
                new VertexEmbeddingProvider(client, vertex), new OpenSearchKnnQueryBuilder(objectMapper),
                new OpenSearchResponseParser(objectMapper), new HttpOpenSearchTransport(), analysis);
        VertexArchitectureFactsExtractor factsExtractor =
                new VertexArchitectureFactsExtractor(client, objectMapper, vertex);
        RetrievalQueryTextBuilder queryTextBuilder = new RetrievalQueryTextBuilder();
        RetrievalModeReferenceRetriever referenceRetriever =
                new RetrievalModeReferenceRetriever(vectorRetriever, analysis);
        VertexGenerationStage vertexGenerationStage =
                new VertexGenerationStage(client, vertex, new VertexPromptBuilder(), new VertexResponseParser(objectMapper));

        EvaluationRunner runner;
        if (configuration.isBroadV4()) {
            AwsProviderSchemaCatalog schemaCatalog = new AwsProviderSchemaCatalog(objectMapper);
            GeneratedTerraformContractInspector contractInspector =
                    new GeneratedTerraformContractInspector(schemaCatalog);
            TerraformDraftValidator draftValidator = new TerraformDraftValidator();
            TerraformCliValidator cliValidator = new TerraformCliValidator(objectMapper);
            VertexGroundedGenerationOrchestrator groundedGeneration = new VertexGroundedGenerationOrchestrator(
                    vertexGenerationStage, referenceRetriever, analysis, schemaCatalog, contractInspector);
            runner = productionEquivalentRunner(factsExtractor, queryTextBuilder, referenceRetriever,
                    groundedGeneration, draftValidator, contractInspector, cliValidator,
                    configuration.retrievalMode(), configuration.identity(), initialEvidenceBudget);
        } else {
            runner = new EvaluationRunner(
                    factsExtractor,
                    queryTextBuilder,
                    referenceRetriever,
                    vertexGenerationStage,
                    new TerraformDraftValidator(),
                    configuration.retrievalMode(),
                    configuration.identity()
            );
        }

        EvaluationRunResult result = runner.run(loaded, configuration.runId());
        Path output = new EvaluationResultWriter(objectMapper).write(configuration.outputFile(), result);
        System.out.printf("live evaluation completed runId=%s mode=%s caseCount=%d output=%s fingerprint=%s%n",
                configuration.runId(), configuration.mode(), result.traces().size(), output,
                configuration.identity().configurationFingerprint());
    }

    static EvaluationRunner productionEquivalentRunner(ArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder, ReferenceRetriever referenceRetriever,
            VertexGroundedGenerationOrchestrator groundedGeneration, TerraformDraftValidator draftValidator,
            GeneratedTerraformContractInspector contractInspector, TerraformCliValidator cliValidator,
            RetrievalMode retrievalMode, EvaluationTrace.ConfigurationIdentity identity) {
        return productionEquivalentRunner(factsExtractor, queryTextBuilder, referenceRetriever,
                groundedGeneration, draftValidator, contractInspector, cliValidator, retrievalMode,
                identity, identity.topK());
    }

    static EvaluationRunner productionEquivalentRunner(ArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder, ReferenceRetriever referenceRetriever,
            VertexGroundedGenerationOrchestrator groundedGeneration, TerraformDraftValidator draftValidator,
            GeneratedTerraformContractInspector contractInspector, TerraformCliValidator cliValidator,
            RetrievalMode retrievalMode, EvaluationTrace.ConfigurationIdentity identity, int initialEvidenceBudget) {
        return new EvaluationRunner(factsExtractor, queryTextBuilder, referenceRetriever, groundedGeneration,
                candidate -> productionEquivalentValidation(candidate, draftValidator, contractInspector, cliValidator),
                "ProductionEquivalentTerraformValidator", retrievalMode, identity, initialEvidenceBudget);
    }

    static int initialEvidenceBudget(LiveEvaluationConfiguration configuration,
            LoadedEvaluationDataset loaded, Map<String, String> environment) {
        String explicit = environment.get("EVALUATION_INITIAL_EVIDENCE_BUDGET");
        if (explicit == null) return configuration.topK();
        if (!configuration.isBroadV4()
                || !loaded.dataset().datasetVersion().equals("terraformers-realistic-v1")
                || !explicit.equals("16")) {
            throw new IllegalArgumentException("explicit initial evidence budget is limited to PT-2 broad-v4 / 16");
        }
        return 16;
    }

    static TerraformDraftValidation productionEquivalentValidation(
            String candidate,
            TerraformDraftValidator draftValidator,
            GeneratedTerraformContractInspector contractInspector,
            TerraformCliValidator cliValidator
    ) {
        TerraformDraftValidation draft = draftValidator.validate(candidate);
        if (!draft.valid()) {
            return draft;
        }
        try {
            contractInspector.inspect(draft.sanitizedContent());
        } catch (GeneratedTerraformContractViolation violation) {
            return new TerraformDraftValidation(
                    false,
                    draft.sanitizedContent(),
                    "AWS_PROVIDER_CONTRACT: " + violation.reason().name()
            );
        }
        return cliValidator.validate(draft.sanitizedContent());
    }

    static LoadedEvaluationDataset select(LoadedEvaluationDataset loaded, LiveEvaluationConfiguration configuration) {
        if (configuration.mode() == LiveEvaluationConfiguration.Mode.FULL) return loaded;
        List<LoadedEvaluationCase> selected = loaded.cases().stream()
                .filter(item -> item.definition().caseId().equals(configuration.caseId())).toList();
        if (selected.size() != 1) {
            throw new IllegalArgumentException("unknown evaluation caseId: " + configuration.caseId());
        }
        EvaluationDataset source = loaded.dataset();
        EvaluationDataset dataset = new EvaluationDataset(
                source.schemaVersion(), source.datasetVersion(), source.description(),
                selected.stream().map(LoadedEvaluationCase::definition).toList());
        return new LoadedEvaluationDataset(dataset, selected);
    }

    static LiveEvaluationConfiguration parse(String[] args, Map<String, String> environment) {
        Map<String, String> cli = new LinkedHashMap<>();
        Arrays.stream(args).forEach(argument -> {
            if (!argument.startsWith("--") || !argument.contains("=")) {
                throw new IllegalArgumentException("arguments must use --name=value syntax");
            }
            int separator = argument.indexOf('=');
            String previous = cli.put(argument.substring(2, separator), argument.substring(separator + 1));
            if (previous != null) throw new IllegalArgumentException("duplicate argument: " + argument.substring(2, separator));
        });
        return new LiveEvaluationConfiguration(
                Path.of(value(cli, environment, "dataset", "EVALUATION_DATASET")),
                Path.of(value(cli, environment, "output", "EVALUATION_OUTPUT")),
                value(cli, environment, "run-id", "EVALUATION_RUN_ID"),
                LiveEvaluationConfiguration.Mode.valueOf(value(cli, environment, "mode", "EVALUATION_MODE").toUpperCase()),
                optional(cli, environment, "case-id", "EVALUATION_CASE_ID"),
                value(cli, environment, "project-id", "GOOGLE_CLOUD_PROJECT"),
                value(cli, environment, "location", "GOOGLE_CLOUD_LOCATION"),
                value(cli, environment, "analysis-provider", "ANALYSIS_PROVIDER"),
                value(cli, environment, "embedding-provider", "EMBEDDING_PROVIDER"),
                com.terraformers.modernization.reference.RetrievalMode.valueOf(
                        value(cli, environment, "retrieval-mode", "RETRIEVAL_MODE").toUpperCase()),
                value(cli, environment, "generation-model", "VERTEX_GENERATION_MODEL_ID"),
                value(cli, environment, "embedding-model", "VERTEX_EMBEDDING_MODEL_ID"),
                value(cli, environment, "opensearch-endpoint", "OPENSEARCH_ENDPOINT"),
                value(cli, environment, "index", "INDEX_NAME"),
                value(cli, environment, "vector-field", "VECTOR_FIELD_NAME"),
                value(cli, environment, "content-field", "CONTENT_FIELD_NAME"),
                value(cli, environment, "corpus", "CORPUS_VERSION"),
                value(cli, environment, "provider-version", "PROVIDER_VERSION"),
                integer(cli, environment, "vector-dimension", "EXPECTED_VECTOR_DIMENSION"),
                integer(cli, environment, "top-k", "OPENSEARCH_TOP_K"),
                integer(cli, environment, "max-output-tokens", "VERTEX_MAX_OUTPUT_TOKENS"));
    }

    private static VertexRuntimeProperties vertexProperties(LiveEvaluationConfiguration c) {
        VertexRuntimeProperties value = new VertexRuntimeProperties();
        value.setProjectId(c.projectId()); value.setLocation(c.location());
        value.setGenerationModelId(c.generationModelId()); value.setEmbeddingModelId(c.embeddingModelId());
        value.setEmbeddingDimension(c.vectorDimension()); value.setMaxOutputTokens(c.maxOutputTokens());
        return value;
    }

    private static AnalysisRuntimeProperties analysisProperties(LiveEvaluationConfiguration c) {
        AnalysisRuntimeProperties value = new AnalysisRuntimeProperties();
        value.setProvider(c.analysisProvider()); value.setEmbeddingProvider(c.embeddingProvider());
        value.setRetrievalMode(c.retrievalMode()); value.setOpensearchTransport("http");
        value.setOpensearchEndpoint(c.openSearchEndpoint()); value.setIndexName(c.indexName());
        value.setVectorFieldName(c.vectorFieldName()); value.setContentFieldName(c.contentFieldName());
        value.setCorpusVersion(c.corpusVersion()); value.setProviderVersion(c.providerVersion());
        value.setExpectedVectorDimension(c.vectorDimension()); value.setOpensearchTopK(c.topK());
        return value;
    }

    private static int integer(Map<String, String> cli, Map<String, String> env, String option, String variable) {
        try { return Integer.parseInt(value(cli, env, option, variable)); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(option + " must be an integer", exception); }
    }

    private static String value(Map<String, String> cli, Map<String, String> env, String option, String variable) {
        String value = optional(cli, env, option, variable);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("--" + option + " or " + variable + " is required");
        return value.strip();
    }

    private static String optional(Map<String, String> cli, Map<String, String> env, String option, String variable) {
        return cli.containsKey(option) ? cli.get(option) : env.get(variable);
    }
}
