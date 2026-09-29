package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import com.terraformers.modernization.reference.RetrievalMode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveEvaluationLauncherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsDeterministicTargetIdentityIndependentOfRunAndFilePaths() {
        LiveEvaluationConfiguration first = configuration("run-one", Path.of("one.json"), Path.of("out-one.json"));
        LiveEvaluationConfiguration second = configuration("run-two", Path.of("two.json"), Path.of("out-two.json"));

        assertThat(first.identity()).isEqualTo(second.identity());
        assertThat(first.identity().configurationFingerprint()).startsWith("sha256:").hasSize(71);
        assertThat(first.identity().analysisProvider()).isEqualTo("vertex");
        assertThat(first.identity().retrievalMode()).isEqualTo("REQUIRED");
        assertThat(first.identity().topK()).isEqualTo(8);
        assertThat(first.identity().factExtractionThinkingLevel()).isEqualTo("LOW");
        assertThat(first.identity().factExtractionMaxOutputTokens()).isEqualTo(800);
        assertThat(first.identity().generationMaxOutputTokens()).isEqualTo(8192);
        assertThat(first.fingerprint()).isEqualTo("sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66");
    }

    @Test
    void readsHistoricalIdentityWithoutA1Provenance() throws Exception {
        String json = """
                {"corpusVersion":"v3","providerVersion":"5.100.0","analysisProvider":"vertex",
                "embeddingProvider":"vertex","retrievalMode":"REQUIRED","topK":8,
                "generationModelId":"generation","embeddingModelId":"embedding","configurationFingerprint":"legacy"}
                """;
        var identity = objectMapper.readValue(json, EvaluationTrace.ConfigurationIdentity.class);
        assertThat(identity.factExtractionThinkingLevel()).isNull();
        assertThat(identity.factExtractionMaxOutputTokens()).isNull();
        assertThat(identity.generationMaxOutputTokens()).isNull();
    }

    @Test
    void rejectsEveryTargetContractMismatch() {
        assertInvalid("ANALYSIS_PROVIDER", "stub");
        assertInvalid("EMBEDDING_PROVIDER", "disabled");
        assertInvalid("RETRIEVAL_MODE", "OPTIONAL");
        assertInvalid("VERTEX_GENERATION_MODEL_ID", "another-model");
        assertInvalid("VERTEX_EMBEDDING_MODEL_ID", "another-model");
        assertInvalid("INDEX_NAME", "another-index");
        assertInvalid("CORPUS_VERSION", "another-corpus");
        assertInvalid("PROVIDER_VERSION", "5.99.0");
        assertInvalid("EXPECTED_VECTOR_DIMENSION", "768");
        assertInvalid("OPENSEARCH_TOP_K", "7");
    }

    @Test
    void selectsExactlyOneRequestedCaseAndRejectsUnknownCase() {
        LoadedEvaluationDataset loaded = loadDataset();
        LiveEvaluationConfiguration selected = configuration(
                "single", datasetPath(), Path.of("target/single.json"));

        LoadedEvaluationDataset result = LiveEvaluationLauncher.select(loaded, selected);

        assertThat(result.cases()).singleElement()
                .extracting(item -> item.definition().caseId()).isEqualTo("arch-vpc-three-tier");
        LiveEvaluationConfiguration unknown = withCase(selected, "does-not-exist");
        assertThatThrownBy(() -> LiveEvaluationLauncher.select(loaded, unknown))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown evaluation caseId");
    }

    @Test
    void fullModeKeepsAllSixFixedCases() {
        LiveEvaluationConfiguration single = configuration("full", datasetPath(), Path.of("target/full.json"));
        LiveEvaluationConfiguration full = new LiveEvaluationConfiguration(
                single.datasetFile(), single.outputFile(), single.runId(), LiveEvaluationConfiguration.Mode.FULL, null,
                single.projectId(), single.location(), single.analysisProvider(), single.embeddingProvider(),
                single.retrievalMode(), single.generationModelId(), single.embeddingModelId(),
                single.openSearchEndpoint(), single.indexName(), single.vectorFieldName(), single.contentFieldName(),
                single.corpusVersion(), single.providerVersion(), single.vectorDimension(), single.topK(),
                single.maxOutputTokens());

        assertThat(LiveEvaluationLauncher.select(loadDataset(), full).cases()).hasSize(6);
    }

    @Test
    void parsesOnlyExplicitCliOrEnvironmentConfigurationWithoutSpring() {
        LiveEvaluationConfiguration parsed = LiveEvaluationLauncher.parse(new String[0], environment());

        assertThat(parsed.mode()).isEqualTo(LiveEvaluationConfiguration.Mode.SINGLE);
        assertThat(parsed.caseId()).isEqualTo("arch-vpc-three-tier");
        assertThat(LiveEvaluationLauncher.class.getAnnotations()).isEmpty();
    }

    private void assertInvalid(String key, String value) {
        Map<String, String> environment = new java.util.HashMap<>(environment());
        environment.put(key, value);
        assertThatThrownBy(() -> LiveEvaluationLauncher.parse(new String[0], environment))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LoadedEvaluationDataset loadDataset() {
        return new EvaluationDatasetLoader(objectMapper).load(datasetPath());
    }

    private LiveEvaluationConfiguration configuration(String runId, Path dataset, Path output) {
        return new LiveEvaluationConfiguration(
                dataset, output, runId, LiveEvaluationConfiguration.Mode.SINGLE, "arch-vpc-three-tier",
                "terraformers-platform", "global", "vertex", "vertex", RetrievalMode.REQUIRED,
                "gemini-3.8-flash", "gemini-embedding-001", "http://terraformers-opensearch:9200",
                "terraformers-reference-v3", "embedding", "content", "terraformers-reference-v3",
                "5.100.0", 1024, 8, 8192);
    }

    private LiveEvaluationConfiguration withCase(LiveEvaluationConfiguration c, String caseId) {
        return new LiveEvaluationConfiguration(
                c.datasetFile(), c.outputFile(), c.runId(), c.mode(), caseId, c.projectId(), c.location(),
                c.analysisProvider(), c.embeddingProvider(), c.retrievalMode(), c.generationModelId(),
                c.embeddingModelId(), c.openSearchEndpoint(), c.indexName(), c.vectorFieldName(),
                c.contentFieldName(), c.corpusVersion(), c.providerVersion(), c.vectorDimension(), c.topK(),
                c.maxOutputTokens());
    }

    private Map<String, String> environment() {
        return Map.ofEntries(
                Map.entry("EVALUATION_DATASET", datasetPath().toString()),
                Map.entry("EVALUATION_OUTPUT", "target/live-result.json"),
                Map.entry("EVALUATION_RUN_ID", "test-run"), Map.entry("EVALUATION_MODE", "single"),
                Map.entry("EVALUATION_CASE_ID", "arch-vpc-three-tier"),
                Map.entry("GOOGLE_CLOUD_PROJECT", "test-project"), Map.entry("GOOGLE_CLOUD_LOCATION", "global"),
                Map.entry("ANALYSIS_PROVIDER", "vertex"), Map.entry("EMBEDDING_PROVIDER", "vertex"),
                Map.entry("RETRIEVAL_MODE", "REQUIRED"),
                Map.entry("VERTEX_GENERATION_MODEL_ID", "gemini-3.8-flash"),
                Map.entry("VERTEX_EMBEDDING_MODEL_ID", "gemini-embedding-001"),
                Map.entry("OPENSEARCH_ENDPOINT", "http://terraformers-opensearch:9200"),
                Map.entry("INDEX_NAME", "terraformers-reference-v3"), Map.entry("VECTOR_FIELD_NAME", "embedding"),
                Map.entry("CONTENT_FIELD_NAME", "content"), Map.entry("CORPUS_VERSION", "terraformers-reference-v3"),
                Map.entry("PROVIDER_VERSION", "5.100.0"), Map.entry("EXPECTED_VECTOR_DIMENSION", "1024"),
                Map.entry("OPENSEARCH_TOP_K", "8"), Map.entry("VERTEX_MAX_OUTPUT_TOKENS", "8192"));
    }

    private Path datasetPath() {
        return Path.of("..", "evaluation", "terraformers-eval-v1", "dataset.json");
    }
}
