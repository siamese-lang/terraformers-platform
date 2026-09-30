package com.terraformers.modernization.evaluation.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.vertex.VertexGenerationStage;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.analysis.vertex.VertexResponseParser;
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Evaluation-only entry point for one frozen Gemini 3.8 Flash MEDIUM-vs-LOW thinking comparison. */
public final class GeminiThinkingComparisonLauncher {
    public static final String SCHEMA_VERSION = "gemini-thinking-comparison-v1";
    public static final String FIXTURE_SCHEMA_VERSION = "opus-generation-fixture-v1";
    public static final String PROMPT_CONTRACT_SOURCE_COMMIT = "3fc610f1e9d601a4d5f79f281b358b783342c4ce";
    public static final String LOCATION = "global";
    public static final String MODEL_ID = "gemini-3.8-flash";
    public static final String CONTROL_THINKING_LEVEL = "MEDIUM";
    public static final String CANDIDATE_THINKING_LEVEL = "LOW";
    public static final int MAX_OUTPUT_TOKENS = 8192;

    private GeminiThinkingComparisonLauncher() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();
        String project = GeminiGenerationComparisonLauncher.required(env, "GOOGLE_CLOUD_PROJECT");
        String location = GeminiGenerationComparisonLauncher.required(env, "GOOGLE_CLOUD_LOCATION");
        String modelId = GeminiGenerationComparisonLauncher.required(env, "THINKING_MODEL_ID");
        String controlThinking = GeminiGenerationComparisonLauncher.required(env, "CONTROL_THINKING_LEVEL");
        String candidateThinking = GeminiGenerationComparisonLauncher.required(env, "CANDIDATE_THINKING_LEVEL");
        int maxTokens = Integer.parseInt(
                GeminiGenerationComparisonLauncher.required(env, "GENERATION_MAX_OUTPUT_TOKENS"));
        requireIdentity(location, modelId, controlThinking, candidateThinking, maxTokens);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var fixture = new OpusGenerationFixtureLoader(mapper).load(
                GeminiGenerationComparisonLauncher.requiredPath(env, "OPUS_FIXTURE_MANIFEST"),
                GeminiGenerationComparisonLauncher.requiredPath(env, "EVALUATION_DATASET"),
                GeminiGenerationComparisonLauncher.requiredPath(env, "CORPUS_DOCUMENTS")
        );
        Client client = Client.builder().project(project).location(LOCATION).vertexAI(true)
                .httpOptions(HttpOptions.builder().apiVersion("v1").build()).build();
        VertexPromptBuilder prompts = new VertexPromptBuilder();
        VertexResponseParser parser = new VertexResponseParser(mapper);
        AnalysisGenerationStage control = stage(client, modelId, controlThinking, prompts, parser);
        AnalysisGenerationStage candidate = stage(client, modelId, candidateThinking, prompts, parser);
        Artifact artifact = run(
                fixture,
                new GeminiGenerationComparisonRunner(control, candidate, modelId, modelId),
                GeminiGenerationComparisonLauncher.required(env, "EVALUATION_RUN_ID"),
                GeminiGenerationComparisonLauncher.required(env, "SOURCE_COMMIT")
        );
        Path output = GeminiGenerationComparisonLauncher.requiredPath(env, "EVALUATION_OUTPUT");
        if (output.toAbsolutePath().getParent() != null) {
            Files.createDirectories(output.toAbsolutePath().getParent());
        }
        mapper.writeValue(output.toFile(), artifact);
    }

    static Artifact run(
            OpusGenerationFixtureLoader.LoadedFixture fixture,
            GeminiGenerationComparisonRunner runner,
            String runId,
            String sourceCommit
    ) {
        List<GeminiGenerationComparisonRunner.PairedCaseEvidence> cases = new ArrayList<>();
        for (var fixtureCase : fixture.cases()) {
            cases.add(runner.evaluate(fixtureCase));
        }
        return new Artifact(
                SCHEMA_VERSION,
                runId,
                sourceCommit,
                PROMPT_CONTRACT_SOURCE_COMMIT,
                FIXTURE_SCHEMA_VERSION,
                fixture.manifestSha256(),
                fixture.datasetVersion(),
                "terraformers-reference-v3",
                "5.100.0",
                LOCATION,
                MODEL_ID,
                CONTROL_THINKING_LEVEL,
                CANDIDATE_THINKING_LEVEL,
                MAX_OUTPUT_TOKENS,
                cases.size(),
                GeminiGenerationComparisonLauncher.armAccepted(cases, true),
                GeminiGenerationComparisonLauncher.armAccepted(cases, false),
                Instant.now().toString(),
                List.copyOf(cases)
        );
    }

    private static AnalysisGenerationStage stage(
            Client client,
            String modelId,
            String thinkingLevel,
            VertexPromptBuilder prompts,
            VertexResponseParser parser
    ) {
        VertexRuntimeProperties properties = new VertexRuntimeProperties();
        properties.setProjectId("evaluation-only");
        properties.setLocation(LOCATION);
        properties.setGenerationModelId(modelId);
        properties.setGenerationThinkingLevel(thinkingLevel);
        properties.setMaxOutputTokens(MAX_OUTPUT_TOKENS);
        return new VertexGenerationStage(client, properties, prompts, parser);
    }

    static void requireIdentity(
            String location,
            String modelId,
            String controlThinking,
            String candidateThinking,
            int maxOutputTokens
    ) {
        if (!LOCATION.equals(location)
                || !MODEL_ID.equals(modelId)
                || !CONTROL_THINKING_LEVEL.equals(controlThinking)
                || !CANDIDATE_THINKING_LEVEL.equals(candidateThinking)
                || maxOutputTokens != MAX_OUTPUT_TOKENS) {
            throw new IllegalArgumentException("Gemini thinking comparison identity does not match the frozen contract");
        }
    }

    public record Artifact(
            String schemaVersion,
            String runId,
            String sourceCommit,
            String promptContractSourceCommit,
            String fixtureSchemaVersion,
            String fixtureManifestSha256,
            String datasetVersion,
            String corpusVersion,
            String providerVersion,
            String location,
            String modelId,
            String controlThinkingLevel,
            String candidateThinkingLevel,
            int maxOutputTokens,
            int caseCount,
            boolean controlMeetsFrozenAcceptance,
            boolean candidateMeetsFrozenAcceptance,
            String createdAt,
            List<GeminiGenerationComparisonRunner.PairedCaseEvidence> cases
    ) {}
}
