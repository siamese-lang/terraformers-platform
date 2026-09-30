package com.terraformers.modernization.evaluation.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.terraformers.modernization.analysis.vertex.VertexGenerationStage;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.analysis.vertex.VertexResponseParser;
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeminiFactReuseComparisonLauncher {

    public static final String SCHEMA_VERSION = "gemini-fact-reuse-comparison-v2";
    public static final String FIXTURE_SCHEMA_VERSION = "opus-generation-fixture-v1";
    public static final String MODEL_ID = "gemini-3.8-flash";
    public static final String LOCATION = "global";
    public static final int GENERATION_MAX_OUTPUT_TOKENS = 8192;
    public static final String CONTROL_MODE = "CURRENT_IMAGE_TWICE";
    public static final String CANDIDATE_MODE = "CANONICAL_FACT_REUSE";
    static final String CASE_IDS_ENV = "FACT_REUSE_CASE_IDS";
    static final List<String> DIAGNOSTIC_CASE_IDS = List.of(
            "arch-cloudfront-private-alb",
            "arch-private-aoss"
    );

    private GeminiFactReuseComparisonLauncher() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> env = System.getenv();
        String project = GeminiGenerationComparisonLauncher.required(env, "GOOGLE_CLOUD_PROJECT");
        String location = GeminiGenerationComparisonLauncher.required(env, "GOOGLE_CLOUD_LOCATION");
        String modelId = GeminiGenerationComparisonLauncher.required(env, "FACT_REUSE_MODEL_ID");
        int maxTokens = Integer.parseInt(
                GeminiGenerationComparisonLauncher.required(env, "GENERATION_MAX_OUTPUT_TOKENS"));
        requireIdentity(location, modelId, maxTokens);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var fixture = new OpusGenerationFixtureLoader(mapper).load(
                GeminiGenerationComparisonLauncher.requiredPath(env, "OPUS_FIXTURE_MANIFEST"),
                GeminiGenerationComparisonLauncher.requiredPath(env, "EVALUATION_DATASET"),
                GeminiGenerationComparisonLauncher.requiredPath(env, "CORPUS_DOCUMENTS")
        );
        List<OpusGenerationFixtureLoader.FixtureCase> selectedCases =
                selectCases(fixture.cases(), env.get(CASE_IDS_ENV));

        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(mapper);
        Client client = Client.builder()
                .project(project)
                .location(LOCATION)
                .vertexAI(true)
                .httpOptions(HttpOptions.builder().apiVersion("v1").build())
                .clientOptions(ClientOptions.builder()
                        .customHttpClient(telemetry.instrumentedHttpClient())
                        .build())
                .build();

        VertexRuntimeProperties runtime = new VertexRuntimeProperties();
        runtime.setProjectId("evaluation-only");
        runtime.setLocation(LOCATION);
        runtime.setGenerationModelId(MODEL_ID);
        runtime.setMaxOutputTokens(GENERATION_MAX_OUTPUT_TOKENS);

        VertexPromptBuilder promptBuilder = new VertexPromptBuilder();
        VertexResponseParser parser = new VertexResponseParser(mapper);

        GeminiCurrentImagePipelineStage control = new GeminiCurrentImagePipelineStage(
                new VertexArchitectureFactsExtractor(client, mapper, runtime),
                new VertexGenerationStage(client, runtime, promptBuilder, parser),
                telemetry,
                new GeminiLatencyTelemetry.RequestIdentity(
                        MODEL_ID, LOCATION, "LOW", VertexArchitectureFactsExtractor.MAX_FACT_TOKENS),
                new GeminiLatencyTelemetry.RequestIdentity(
                        MODEL_ID, LOCATION, "DEFAULT", GENERATION_MAX_OUTPUT_TOKENS)
        );

        GeminiFactReusePipelineStage candidate = new GeminiFactReusePipelineStage(
                new GeminiCanonicalEnvelopeExtractor(client, mapper, MODEL_ID, telemetry),
                new GeminiFactReuseGenerationStage(
                        client,
                        MODEL_ID,
                        GENERATION_MAX_OUTPUT_TOKENS,
                        mapper,
                        promptBuilder,
                        parser,
                        telemetry
                ),
                telemetry,
                new GeminiLatencyTelemetry.RequestIdentity(
                        MODEL_ID, LOCATION, "LOW", GeminiCanonicalEnvelopeExtractor.MAX_OUTPUT_TOKENS),
                new GeminiLatencyTelemetry.RequestIdentity(
                        MODEL_ID, LOCATION, "MEDIUM", GENERATION_MAX_OUTPUT_TOKENS)
        );

        GeminiFactReuseComparisonRunner runner = new GeminiFactReuseComparisonRunner(
                control,
                candidate,
                MODEL_ID,
                telemetry
        );

        List<GeminiFactReuseComparisonRunner.CaseEvidence> cases = new ArrayList<>();
        for (var fixtureCase : selectedCases) {
            cases.add(runner.evaluate(fixtureCase));
        }

        Artifact artifact = new Artifact(
                SCHEMA_VERSION,
                GeminiGenerationComparisonLauncher.required(env, "EVALUATION_RUN_ID"),
                GeminiGenerationComparisonLauncher.required(env, "SOURCE_COMMIT"),
                FIXTURE_SCHEMA_VERSION,
                fixture.manifestSha256(),
                fixture.datasetVersion(),
                "terraformers-reference-v3",
                "5.100.0",
                LOCATION,
                MODEL_ID,
                CONTROL_MODE,
                CANDIDATE_MODE,
                GENERATION_MAX_OUTPUT_TOKENS,
                cases.size(),
                armAccepted(cases, true),
                armAccepted(cases, false),
                canonicalClassificationAccepted(cases),
                Instant.now().toString(),
                List.copyOf(cases)
        );

        Path output = GeminiGenerationComparisonLauncher.requiredPath(env, "EVALUATION_OUTPUT");
        if (output.toAbsolutePath().getParent() != null) {
            Files.createDirectories(output.toAbsolutePath().getParent());
        }
        mapper.writeValue(output.toFile(), artifact);
    }

    static List<OpusGenerationFixtureLoader.FixtureCase> selectCases(
            List<OpusGenerationFixtureLoader.FixtureCase> fixtureCases,
            String requestedCaseIds
    ) {
        if (requestedCaseIds == null || requestedCaseIds.isBlank()) {
            return List.copyOf(fixtureCases);
        }

        List<String> requested = List.of(requestedCaseIds.split(",", -1)).stream()
                .map(String::trim)
                .toList();
        Set<String> unique = new HashSet<>(requested);
        if (unique.size() != requested.size()) {
            throw new IllegalArgumentException("FACT_REUSE case IDs must not contain duplicates");
        }
        Map<String, OpusGenerationFixtureLoader.FixtureCase> byId = fixtureCases.stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.definition().caseId(), c -> c));
        List<String> unknown = requested.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown FACT_REUSE case IDs: " + unknown);
        }
        if (!requested.equals(DIAGNOSTIC_CASE_IDS)) {
            throw new IllegalArgumentException(
                    "FACT_REUSE targeted selection must match the frozen diagnostic case IDs");
        }
        return requested.stream().map(byId::get).toList();
    }

    static boolean armAccepted(
            List<GeminiFactReuseComparisonRunner.CaseEvidence> cases,
            boolean control
    ) {
        return cases.size() == 6 && cases.stream()
                .map(c -> control ? c.control() : c.candidate())
                .allMatch(arm -> "NONE".equals(arm.firstFailureCategory()));
    }

    static boolean canonicalClassificationAccepted(
            List<GeminiFactReuseComparisonRunner.CaseEvidence> cases
    ) {
        return cases.size() == 6 && cases.stream().allMatch(
                c -> c.expectedClassification().equals(c.canonicalInputType()));
    }

    static void requireIdentity(String location, String modelId, int maxOutputTokens) {
        if (!LOCATION.equals(location)
                || !MODEL_ID.equals(modelId)
                || maxOutputTokens != GENERATION_MAX_OUTPUT_TOKENS) {
            throw new IllegalArgumentException(
                    "Gemini fact reuse comparison identity does not match the frozen contract");
        }
    }

    record Artifact(
            String schemaVersion,
            String runId,
            String sourceCommit,
            String fixtureSchemaVersion,
            String fixtureManifestSha256,
            String datasetVersion,
            String corpusVersion,
            String providerVersion,
            String location,
            String modelId,
            String controlMode,
            String candidateMode,
            int maxOutputTokens,
            int caseCount,
            boolean controlMeetsFrozenAcceptance,
            boolean candidateMeetsFrozenAcceptance,
            boolean canonicalClassificationMeetsFrozenAcceptance,
            String createdAt,
            List<GeminiFactReuseComparisonRunner.CaseEvidence> cases
    ) {}
}
