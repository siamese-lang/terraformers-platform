package com.terraformers.modernization.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Worker-local originals. Never log or serialize this collector into the ordinary job response. */
public final class AnalysisDiagnosticEvidence implements AutoCloseable {
    public static final int MAX_CANDIDATE_BYTES = 256 * 1024;
    public static final int MAX_BUNDLE_BYTES = 1024 * 1024;
    private static final ThreadLocal<AnalysisDiagnosticEvidence> CURRENT = new ThreadLocal<>();
    private final Map<String, Object> stages = new LinkedHashMap<>();
    private final Map<String, Object> candidates = new LinkedHashMap<>();
    private final Map<String, Object> retrieval = new LinkedHashMap<>();
    private final String jobId;
    private final Long projectId;
    private final long generation;
    private String activeStage = "provider";
    private String lastOutputId;
    private Map<String, Object> facts = Map.of("status", "NOT_CAPTURED", "boundaries", "NOT_CAPTURED");
    private Map<String, Object> failure;
    private TerraformDiagnosticSummary cliDiagnostics;
    private boolean bounded = true;

    private AnalysisDiagnosticEvidence(AnalysisJobEntity job) {
        jobId = job.getId(); projectId = job.getProjectId(); generation = job.getClaimGeneration();
        lastOutputId = "source-file:" + job.getSourceFileId();
        for (String stage : List.of("source_read", "facts", "retrieval", "initial_generation", "closure", "repair",
                "final_evidence", "draft_validation", "cli_init", "cli_validate", "result_finalization")) {
            stages.put(stage, Map.of("status", "NOT_CAPTURED"));
        }
    }

    public static AnalysisDiagnosticEvidence open(AnalysisJobEntity job) {
        if (CURRENT.get() != null) throw new IllegalStateException("diagnostic scope already active");
        var evidence = new AnalysisDiagnosticEvidence(job);
        CURRENT.set(evidence);
        return evidence;
    }
    public static AnalysisDiagnosticEvidence current() { return CURRENT.get(); }
    public static void stage(String stage) { if (current() != null) current().start(stage); }
    public void start(String stage) {
        activeStage = stage;
        stages.put(stage, Map.of("status", "STARTED", "inputId", lastOutputId));
    }
    public void captured(String stage) { captured(stage, stage + ":" + lastOutputId); }
    private void captured(String stage, String outputId) {
        Object old = stages.get(stage);
        Object input = old instanceof Map<?, ?> map ? map.getOrDefault("inputId", null) : null;
        stages.put(stage, Map.of("status", "CAPTURED", "inputId", input == null ? lastOutputId : input,
                "outputId", outputId));
        lastOutputId = outputId;
    }

    public void source(byte[] bytes) { captured("source_read", "sha256:" + sha(bytes)); }

    public void fenced() {
        start("ownership_fence");
        failure = Map.of("stage", "ownership_fence", "category", "OWNERSHIP_OR_DEADLINE_LOST",
                "exceptionType", "AnalysisFinalizationFence");
        stages.put("ownership_fence", Map.of("status", "FAILED"));
    }

    public void facts(ArchitectureRetrievalFacts value) {
        facts = Map.of("status", "CAPTURED", "summary", value.summary(), "components", value.components(),
                "relationships", value.relationships(), "resourceTypes", value.resourceTypes(),
                "boundaries", "NOT_CAPTURED"); // The existing extractor has no boundary field.
        captured("facts", "sha256:" + sha(value.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public void references(String stage, List<ReferenceDocument> documents) {
        retrieval.put(stage, documents.stream().limit(256).map(document -> Map.<String, Object>of(
                "id", document.id(), "resourceTypes", document.resourceTypes(),
                "officialProviderDocumentation", document.isOfficialProviderDocumentation(),
                "selectionBoundary", switch (stage) {
                    case "initial_context", "repair_context" -> "MODEL_CONTEXT_ARGUMENT";
                    case "final_evidence" -> "FINAL_DOCUMENT_BINDING";
                    case "closure" -> "GENERATED_RESOURCE_CLOSURE_HIT";
                    default -> "RETRIEVED_HIT";
                },
                "score", document.score(), "priority", document.priority(),
                "metadata", Map.of("sourcePath", text(document.sourcePath()), "providerVersion", text(document.providerVersion()),
                        "corpusVersion", text(document.corpusVersion()), "authority", text(document.authority()),
                        "documentType", text(document.documentType()), "sourceCommit", "NOT_CAPTURED"))).toList());
        if (documents.size() > 256) bounded = false;
        captured(stage, "sha256:" + sha(documents.stream().map(ReferenceDocument::id).toList().toString()
                .getBytes(StandardCharsets.UTF_8)));
    }

    public void candidate(String name, String hcl) {
        if (hcl == null) return;
        byte[] bytes = hcl.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CANDIDATE_BYTES) {
            bounded = false;
            candidates.put(name, Map.of("status", "SIZE_LIMIT_EXCEEDED", "sha256", sha(bytes), "bytes", bytes.length));
        } else {
            candidates.put(name, Map.of("status", "CAPTURED", "sha256", sha(bytes), "bytes", bytes.length,
                    "content", hcl));
            lastOutputId = "sha256:" + sha(bytes);
        }
    }

    public void cli(TerraformDraftValidation validation) {
        String stage = validation.reason() != null && validation.reason().startsWith("INIT_")
                || validation.reason() != null && validation.reason().startsWith("PROVIDER_CLOSURE:")
                ? "cli_init" : "cli_validate";
        if ("init".equals(validation.executionPhase())) stage = "cli_init";
        activeStage = stage;
        if (stage.equals("cli_validate")) captured("cli_init");
        cliDiagnostics = validation.diagnosticSummary();
        var execution = new LinkedHashMap<String, Object>();
        execution.put("status", validation.valid() ? "CAPTURED" : "FAILED");
        execution.put("exitCode", validation.exitCode()); execution.put("elapsedMs", validation.elapsedMs());
        stages.put(stage, execution);
    }

    public void failed(RuntimeException exception) {
        String category = "UNCLASSIFIED_INTERNAL_FAILURE";
        if (exception instanceof TerraformValidationFailureException cli) category = cli.category().name();
        else if (exception instanceof AnalysisProviderTimeoutException) category = "PROVIDER_TIMEOUT";
        else if (exception instanceof AnalysisProviderFailureException provider) category = provider.reason().name();
        else if (exception instanceof com.terraformers.modernization.reference.ArchitectureFactsExtractionException factsFailure)
            category = "FACTS_" + factsFailure.reason().name();
        else if (exception instanceof com.terraformers.modernization.storage.ObjectStorageException sourceFailure)
            category = "OBJECT_STORAGE_" + sourceFailure.reason().name();
        failure = Map.of("stage", activeStage, "category", category,
                "exceptionType", exception.getClass().getSimpleName()); // Class only; no exception message/cause text.
        var detail = new LinkedHashMap<String, Object>();
        Object existing = stages.get(activeStage);
        if (existing instanceof Map<?, ?> map) map.forEach((key, value) -> detail.put(String.valueOf(key), value));
        detail.put("status", "FAILED"); stages.put(activeStage, detail);
    }

    public Map<String, Object> bundle(ObjectMapper mapper) {
        var value = new LinkedHashMap<String, Object>();
        value.put("contractVersion", "analysis-diagnostics-v1");
        value.put("jobId", jobId); value.put("projectId", projectId); value.put("claimGeneration", generation);
        boolean complete = bounded;
        if (failure == null) complete &= candidates.containsKey("final");
        else {
            String stage = String.valueOf(failure.get("stage"));
            if (List.of("draft_validation", "cli_init", "cli_validate", "result_finalization").contains(stage))
                complete &= candidates.containsKey("final");
            if (stage.startsWith("cli_")) complete &= candidates.containsKey("validated");
            if (List.of("INIT_CONFIGURATION", "VALIDATE_CONFIGURATION").contains(failure.get("category")))
                complete &= cliDiagnostics != null && candidates.containsKey("validated");
        }
        value.put("complete", complete);
        value.put("stages", stages); value.put("facts", facts); value.put("retrieval", retrieval);
        value.put("candidates", candidates); value.put("failure", failure);
        value.put("cliDiagnostics", cliDiagnostics);
        return value;
    }

    /** Public measurement export contains identifiers, hashes and allowlisted diagnostics, never originals. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> summary(Map<String, Object> bundle) {
        var result = new LinkedHashMap<>(bundle);
        var safeCandidates = new LinkedHashMap<String, Object>();
        ((Map<String, Object>) bundle.getOrDefault("candidates", Map.of())).forEach((name, value) -> {
            var safe = new LinkedHashMap<>((Map<String, Object>) value);
            safe.remove("content"); safeCandidates.put(name, safe);
        });
        result.put("candidates", safeCandidates);
        result.remove("facts"); result.remove("retrieval");
        result.put("factsStatus", ((Map<String, Object>) bundle.getOrDefault("facts", Map.of())).get("status"));
        return result;
    }

    private static String text(String value) { return value == null ? "" : value; }

    public static String sha(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    @Override public void close() { CURRENT.remove(); }
}
