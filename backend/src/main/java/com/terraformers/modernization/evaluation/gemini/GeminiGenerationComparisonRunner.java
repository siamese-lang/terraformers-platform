package com.terraformers.modernization.evaluation.gemini;

import com.terraformers.modernization.analysis.*;
import com.terraformers.modernization.analysis.vertex.VertexPromptBuilder;
import com.terraformers.modernization.evaluation.EvaluationCase;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs the two fixed generation arms without retrieval, fact extraction, or model-specific prompts. */
public final class GeminiGenerationComparisonRunner {
    public static final String CONTROL_MODEL_ID = "gemini-3.8-flash";
    public static final String CANDIDATE_MODEL_ID = "gemini-3.1-pro-preview";
    private static final Pattern RESOURCE = Pattern.compile("(?m)^\\s*resource\\s+\"([^\"]+)\"\\s+\"");

    private final AnalysisGenerationStage control;
    private final AnalysisGenerationStage candidate;
    private final VertexPromptBuilder prompts;
    private final TerraformDraftValidator validator;

    public GeminiGenerationComparisonRunner(AnalysisGenerationStage control, AnalysisGenerationStage candidate) {
        this(control, candidate, new VertexPromptBuilder(), new TerraformDraftValidator());
    }

    GeminiGenerationComparisonRunner(AnalysisGenerationStage control, AnalysisGenerationStage candidate,
                                     VertexPromptBuilder prompts, TerraformDraftValidator validator) {
        this.control = Objects.requireNonNull(control);
        this.candidate = Objects.requireNonNull(candidate);
        this.prompts = Objects.requireNonNull(prompts);
        this.validator = Objects.requireNonNull(validator);
    }

    public PairedCaseEvidence evaluate(OpusGenerationFixtureLoader.FixtureCase fixture) {
        ObjectContent source = source(fixture);
        String controlPrompt = prompts.build(source, fixture.references(), false);
        String candidatePrompt = prompts.build(source, fixture.references(), false);
        String controlHash = sha256(controlPrompt);
        String candidateHash = sha256(candidatePrompt);
        if (!controlPrompt.equals(candidatePrompt) || !controlHash.equals(candidateHash)) {
            throw new IllegalStateException("SETUP_OR_FIXTURE: model arms received different semantic prompts");
        }
        ArmEvidence controlEvidence = invoke(CONTROL_MODEL_ID, control, fixture, source, controlHash);
        ArmEvidence candidateEvidence = invoke(CANDIDATE_MODEL_ID, candidate, fixture, source, candidateHash);
        return new PairedCaseEvidence(fixture.definition().caseId(), fixture.definition().expectedClassification().name(),
                fixture.referenceIds(), controlHash, controlEvidence, candidateEvidence);
    }

    private ArmEvidence invoke(String modelId, AnalysisGenerationStage stage,
                               OpusGenerationFixtureLoader.FixtureCase fixture, ObjectContent source, String promptHash) {
        long started = System.nanoTime();
        try {
            AnalysisGenerationResult result = stage.generate(
                    new AnalysisRequestContext("gemini-comparison", "evaluation", "fixture",
                            fixture.definition().input().path(), fixture.definition().caseId(), AnalysisMode.INTEGRATED_JAVA),
                    source, fixture.references());
            return score(modelId, fixture, result, elapsed(started), promptHash);
        } catch (AnalysisInputRejectedException rejected) {
            return scoreRejected(modelId, fixture, rejected, elapsed(started), promptHash);
        } catch (AnalysisGenerationOutputTruncatedException truncated) {
            return failure(modelId, fixture, promptHash, elapsed(started), "OUTPUT_TRUNCATED",
                    "model output remained truncated after the compact retry", true);
        } catch (AnalysisGenerationResponseFormatException format) {
            return failure(modelId, fixture, promptHash, elapsed(started), "RESPONSE_FORMAT",
                    safeReason(format), false);
        } catch (RuntimeException provider) {
            return failure(modelId, fixture, promptHash, elapsed(started), "PROVIDER_RUNTIME",
                    safeReason(provider), false);
        }
    }

    private ArmEvidence score(String modelId, OpusGenerationFixtureLoader.FixtureCase fixture,
                              AnalysisGenerationResult result, long latency, String promptHash) {
        String terraform = result.terraformCode();
        Scoring scoring = scoring(fixture, terraform);
        boolean responseFormatPassed = fixture.definition().generation().terraformExpected() || terraform.isBlank();
        String category = category(fixture, result.inputClassification(), scoring, responseFormatPassed, result.stopReason());
        return new ArmEvidence(modelId, result.inputClassification().name(), result.classificationConfidence(),
                fixture.referenceIds(), promptHash, terraform, scoring.resources(), scoring.matched(), scoring.missing(),
                scoring.forbidden(), scoring.validation().valid(), scoring.validation().reason(), scoring.placeholder(),
                !terraform.isBlank(), responseFormatPassed, latency, result.outputTokens(), result.stopReason(), result.retryOccurred(),
                category, null);
    }

    private ArmEvidence scoreRejected(String modelId, OpusGenerationFixtureLoader.FixtureCase fixture,
                                      AnalysisInputRejectedException rejected, long latency, String promptHash) {
        Scoring scoring = scoring(fixture, "");
        String category = category(fixture, rejected.classification(), scoring, true, "");
        return new ArmEvidence(modelId, rejected.classification().name(), rejected.classificationConfidence(),
                fixture.referenceIds(), promptHash, "", scoring.resources(), scoring.matched(), scoring.missing(),
                scoring.forbidden(), scoring.validation().valid(), scoring.validation().reason(), false, false, true,
                latency, null, "", rejected.retryOccurred(), category, null);
    }

    private ArmEvidence failure(String modelId, OpusGenerationFixtureLoader.FixtureCase fixture, String promptHash,
                                long latency, String category, String reason, boolean retry) {
        return new ArmEvidence(modelId, null, null, fixture.referenceIds(), promptHash, "", List.of(), List.of(),
                fixture.definition().generation().terraformResourceTypes().required(), List.of(), false, reason,
                false, false, false, latency, null, "", retry, category, reason);
    }

    private Scoring scoring(OpusGenerationFixtureLoader.FixtureCase fixture, String terraform) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = RESOURCE.matcher(terraform == null ? "" : terraform);
        while (matcher.find()) found.add(matcher.group(1));
        EvaluationCase.TextExpectation expected = fixture.definition().generation().terraformResourceTypes();
        List<String> matched = expected.required().stream().filter(found::contains).toList();
        List<String> missing = expected.required().stream().filter(v -> !found.contains(v)).toList();
        List<String> forbidden = expected.forbidden().stream().filter(found::contains).toList();
        TerraformDraftValidation validation = fixture.definition().generation().terraformExpected()
                ? validator.validate(terraform) : new TerraformDraftValidation(true, "", null);
        boolean placeholder = validation.reason() != null && validation.reason().contains("placeholder/example");
        return new Scoring(List.copyOf(found), matched, missing, forbidden, validation, placeholder);
    }

    private String category(OpusGenerationFixtureLoader.FixtureCase fixture, AnalysisInputClassification observed,
                            Scoring scoring, boolean responseFormatPassed, String stopReason) {
        if ("MAX_TOKENS".equalsIgnoreCase(stopReason)) return "OUTPUT_TRUNCATED";
        if (!responseFormatPassed) return "RESPONSE_FORMAT";
        if (!fixture.definition().expectedClassification().name().equals(observed.name())) return "INPUT_CLASSIFICATION";
        if (!scoring.missing().isEmpty()) return "GENERATION_REQUIRED_RESOURCE_MISSING";
        if (!scoring.forbidden().isEmpty()) return "GENERATION_FORBIDDEN_RESOURCE";
        if (fixture.definition().generation().terraformExpected() && !scoring.validation().valid())
            return "TERRAFORM_STRUCTURAL_VALIDATION";
        return "NONE";
    }

    private static ObjectContent source(OpusGenerationFixtureLoader.FixtureCase fixture) {
        return new ObjectContent(new ObjectMetadata("fixture", fixture.definition().input().path(),
                fixture.definition().input().contentType(), fixture.imageBytes().length, ""), fixture.imageBytes());
    }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
    private static String safeReason(RuntimeException exception) {
        return exception.getClass().getSimpleName();
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private record Scoring(List<String> resources, List<String> matched, List<String> missing,
                           List<String> forbidden, TerraformDraftValidation validation, boolean placeholder) {}
    public record PairedCaseEvidence(String caseId, String expectedClassification, List<String> suppliedReferenceIds,
                                     String renderedPromptSha256, ArmEvidence control, ArmEvidence candidate) {}
    public record ArmEvidence(String modelId, String observedClassification, Double classificationConfidence,
                              List<String> suppliedOrderedReferenceIds, String renderedPromptSha256,
                              String generatedTerraform, List<String> generatedTerraformResourceTypes,
                              List<String> requiredResourceTypesMatched, List<String> requiredResourceTypesMissing,
                              List<String> forbiddenResourceTypesPresent, boolean terraformDraftValidatorPassed,
                              String terraformDraftValidatorReason, boolean placeholderExampleDetected,
                              boolean terraformEmitted, boolean responseFormatPassed, long latencyMs,
                              Integer outputTokens, String stopReason, boolean compactRetryOccurred,
                              String firstFailureCategory, String safeFailureReason) {}
}
