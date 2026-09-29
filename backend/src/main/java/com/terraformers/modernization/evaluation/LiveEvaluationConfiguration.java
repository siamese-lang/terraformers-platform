package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Explicit, fail-closed configuration for the bounded GCP live evaluation command. */
public record LiveEvaluationConfiguration(
        Path datasetFile,
        Path outputFile,
        String runId,
        Mode mode,
        String caseId,
        String projectId,
        String location,
        String analysisProvider,
        String embeddingProvider,
        RetrievalMode retrievalMode,
        String generationModelId,
        String embeddingModelId,
        String openSearchEndpoint,
        String indexName,
        String vectorFieldName,
        String contentFieldName,
        String corpusVersion,
        String providerVersion,
        int vectorDimension,
        int topK,
        int maxOutputTokens
) {
    static final String TARGET_ANALYSIS_PROVIDER = "vertex";
    static final String TARGET_EMBEDDING_PROVIDER = "vertex";
    static final String TARGET_GENERATION_MODEL = "gemini-3.8-flash";
    static final String TARGET_EMBEDDING_MODEL = "gemini-embedding-001";
    static final String TARGET_INDEX = "terraformers-reference-v3";
    static final String TARGET_CORPUS = "terraformers-reference-v3";
    static final String TARGET_PROVIDER_VERSION = "5.100.0";
    static final int TARGET_VECTOR_DIMENSION = 1024;
    static final int TARGET_TOP_K = 8;

    public LiveEvaluationConfiguration {
        datasetFile = Objects.requireNonNull(datasetFile, "datasetFile").toAbsolutePath().normalize();
        outputFile = Objects.requireNonNull(outputFile, "outputFile").toAbsolutePath().normalize();
        runId = requireText(runId, "runId");
        mode = Objects.requireNonNull(mode, "mode");
        caseId = normalize(caseId);
        projectId = requireText(projectId, "projectId");
        location = requireText(location, "location");
        analysisProvider = requireTarget(analysisProvider, TARGET_ANALYSIS_PROVIDER, "analysisProvider");
        embeddingProvider = requireTarget(embeddingProvider, TARGET_EMBEDDING_PROVIDER, "embeddingProvider");
        retrievalMode = Objects.requireNonNull(retrievalMode, "retrievalMode");
        requireTarget(retrievalMode.name(), RetrievalMode.REQUIRED.name(), "retrievalMode");
        generationModelId = requireTarget(generationModelId, TARGET_GENERATION_MODEL, "generationModelId");
        embeddingModelId = requireTarget(embeddingModelId, TARGET_EMBEDDING_MODEL, "embeddingModelId");
        openSearchEndpoint = requireText(openSearchEndpoint, "openSearchEndpoint");
        indexName = requireTarget(indexName, TARGET_INDEX, "indexName");
        vectorFieldName = requireText(vectorFieldName, "vectorFieldName");
        contentFieldName = requireText(contentFieldName, "contentFieldName");
        corpusVersion = requireTarget(corpusVersion, TARGET_CORPUS, "corpusVersion");
        providerVersion = requireTarget(providerVersion, TARGET_PROVIDER_VERSION, "providerVersion");
        requireTarget(vectorDimension, TARGET_VECTOR_DIMENSION, "vectorDimension");
        requireTarget(topK, TARGET_TOP_K, "topK");
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("maxOutputTokens must be positive");
        if (mode == Mode.SINGLE && caseId.isBlank()) {
            throw new IllegalArgumentException("caseId is required in single mode");
        }
        if (mode == Mode.FULL && !caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must be omitted in full mode");
        }
    }

    public ConfigurationIdentity identity() {
        return new ConfigurationIdentity(
                corpusVersion, providerVersion, analysisProvider, embeddingProvider,
                retrievalMode.name(), topK, generationModelId, embeddingModelId, fingerprint(),
                VertexArchitectureFactsExtractor.FACT_EXTRACTION_THINKING_LEVEL.name(),
                VertexArchitectureFactsExtractor.MAX_FACT_TOKENS, maxOutputTokens);
    }

    /** Hashes only effective runtime behavior, never run IDs or filesystem paths. */
    public String fingerprint() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("analysisProvider", analysisProvider);
        values.put("embeddingProvider", embeddingProvider);
        values.put("retrievalMode", retrievalMode.name());
        values.put("generationModelId", generationModelId);
        values.put("embeddingModelId", embeddingModelId);
        values.put("projectId", projectId);
        values.put("location", location);
        values.put("openSearchEndpoint", openSearchEndpoint);
        values.put("indexName", indexName);
        values.put("vectorFieldName", vectorFieldName);
        values.put("contentFieldName", contentFieldName);
        values.put("corpusVersion", corpusVersion);
        values.put("providerVersion", providerVersion);
        values.put("vectorDimension", Integer.toString(vectorDimension));
        values.put("topK", Integer.toString(topK));
        values.put("maxOutputTokens", Integer.toString(maxOutputTokens));
        String canonical = values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public enum Mode { SINGLE, FULL }

    private static String requireTarget(String actual, String expected, String field) {
        String value = requireText(actual, field);
        if (!expected.equals(value)) throw new IllegalArgumentException(field + " must be " + expected);
        return value;
    }

    private static void requireTarget(int actual, int expected, String field) {
        if (actual != expected) throw new IllegalArgumentException(field + " must be " + expected);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
