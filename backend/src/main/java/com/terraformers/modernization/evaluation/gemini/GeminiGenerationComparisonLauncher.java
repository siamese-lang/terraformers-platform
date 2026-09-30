package com.terraformers.modernization.evaluation.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.vertex.*;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Standalone, evaluation-only entry point for one paired Flash/Pro run. */
public final class GeminiGenerationComparisonLauncher {
    public static final String SCHEMA_VERSION = "gemini-generation-comparison-v1";
    public static final String FIXTURE_SCHEMA_VERSION = "opus-generation-fixture-v1";
    public static final String PROMPT_CONTRACT_SOURCE_COMMIT = "3fc610f1e9d601a4d5f79f281b358b783342c4ce";
    public static final String LOCATION = "global";
    public static final int MAX_OUTPUT_TOKENS = 8192;

    private GeminiGenerationComparisonLauncher() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();
        String project = required(env, "GOOGLE_CLOUD_PROJECT");
        String location = required(env, "GOOGLE_CLOUD_LOCATION");
        String controlId = required(env, "CONTROL_MODEL_ID");
        String candidateId = required(env, "CANDIDATE_MODEL_ID");
        int maxTokens = Integer.parseInt(required(env, "GENERATION_MAX_OUTPUT_TOKENS"));
        requireIdentity(location, controlId, candidateId, maxTokens);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var fixture = new OpusGenerationFixtureLoader(mapper).load(requiredPath(env, "OPUS_FIXTURE_MANIFEST"),
                requiredPath(env, "EVALUATION_DATASET"), requiredPath(env, "CORPUS_DOCUMENTS"));
        Client client = Client.builder().project(project).location(LOCATION).vertexAI(true)
                .httpOptions(HttpOptions.builder().apiVersion("v1").build()).build();
        VertexPromptBuilder prompts = new VertexPromptBuilder();
        VertexResponseParser parser = new VertexResponseParser(mapper);
        AnalysisGenerationStage control = stage(client, controlId, prompts, parser);
        AnalysisGenerationStage candidate = stage(client, candidateId, prompts, parser);
        Artifact artifact = run(fixture, new GeminiGenerationComparisonRunner(control, candidate),
                required(env, "EVALUATION_RUN_ID"), required(env, "SOURCE_COMMIT"));
        Path output = requiredPath(env, "EVALUATION_OUTPUT");
        if (output.toAbsolutePath().getParent() != null) Files.createDirectories(output.toAbsolutePath().getParent());
        mapper.writeValue(output.toFile(), artifact);
    }

    static Artifact run(OpusGenerationFixtureLoader.LoadedFixture fixture,
                        GeminiGenerationComparisonRunner runner, String runId, String sourceCommit) {
        List<GeminiGenerationComparisonRunner.PairedCaseEvidence> cases = new ArrayList<>();
        for (var fixtureCase : fixture.cases()) cases.add(runner.evaluate(fixtureCase));
        return new Artifact(SCHEMA_VERSION, runId, sourceCommit, PROMPT_CONTRACT_SOURCE_COMMIT,
                FIXTURE_SCHEMA_VERSION, fixture.manifestSha256(), fixture.datasetVersion(),
                "terraformers-reference-v3", "5.100.0", LOCATION, MAX_OUTPUT_TOKENS,
                GeminiGenerationComparisonRunner.CONTROL_MODEL_ID,
                GeminiGenerationComparisonRunner.CANDIDATE_MODEL_ID, cases.size(),
                armAccepted(cases, true), armAccepted(cases, false), Instant.now().toString(), List.copyOf(cases));
    }

    static boolean armAccepted(List<GeminiGenerationComparisonRunner.PairedCaseEvidence> cases, boolean control) {
        return cases.size() == 6 && cases.stream().map(c -> control ? c.control() : c.candidate())
                .allMatch(a -> "NONE".equals(a.firstFailureCategory()));
    }

    private static AnalysisGenerationStage stage(Client client, String modelId, VertexPromptBuilder prompts,
                                                  VertexResponseParser parser) {
        VertexRuntimeProperties properties = new VertexRuntimeProperties();
        properties.setProjectId("evaluation-only");
        properties.setLocation(LOCATION);
        properties.setGenerationModelId(modelId);
        properties.setMaxOutputTokens(MAX_OUTPUT_TOKENS);
        return new VertexGenerationStage(client, properties, prompts, parser);
    }

    static void requireIdentity(String location, String control, String candidate, int max) {
        if (!LOCATION.equals(location)
                || !GeminiGenerationComparisonRunner.CONTROL_MODEL_ID.equals(control)
                || !GeminiGenerationComparisonRunner.CANDIDATE_MODEL_ID.equals(candidate)
                || max != MAX_OUTPUT_TOKENS) {
            throw new IllegalArgumentException("Gemini comparison identity does not match the frozen contract");
        }
    }
    static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
    static Path requiredPath(Map<String, String> env, String key) { return Path.of(required(env, key)); }

    public record Artifact(String schemaVersion, String runId, String sourceCommit,
                           String promptContractSourceCommit, String fixtureSchemaVersion,
                           String fixtureManifestSha256, String datasetVersion, String corpusVersion,
                           String providerVersion, String location, int maxOutputTokens,
                           String controlModelId, String candidateModelId, int caseCount,
                           boolean controlMeetsFrozenAcceptance, boolean candidateMeetsFrozenAcceptance,
                           String createdAt, List<GeminiGenerationComparisonRunner.PairedCaseEvidence> cases) {}
}
