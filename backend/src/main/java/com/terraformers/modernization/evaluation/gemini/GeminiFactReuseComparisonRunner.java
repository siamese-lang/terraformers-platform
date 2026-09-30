package com.terraformers.modernization.evaluation.gemini;

import com.terraformers.modernization.analysis.AnalysisGenerationOutputTruncatedException;
import com.terraformers.modernization.analysis.AnalysisGenerationResponseFormatException;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.TerraformDraftValidation;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.evaluation.EvaluationCase;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GeminiFactReuseComparisonRunner {

    private static final Pattern RESOURCE = Pattern.compile("(?m)^\\s*resource\\s+\"([^\"]+)\"\\s+\"");
    private final AnalysisGenerationStage control;
    private final GeminiFactReusePipelineStage candidate;
    private final String modelId;
    private final TerraformDraftValidator validator = new TerraformDraftValidator();

    GeminiFactReuseComparisonRunner(
            AnalysisGenerationStage control,
            GeminiFactReusePipelineStage candidate,
            String modelId
    ) {
        this.control = control;
        this.candidate = candidate;
        this.modelId = modelId;
    }

    CaseEvidence evaluate(OpusGenerationFixtureLoader.FixtureCase fixture) {
        ObjectContent source = source(fixture);
        AnalysisRequestContext context = new AnalysisRequestContext(
                "gemini-fact-reuse-comparison",
                "evaluation",
                "fixture",
                fixture.definition().input().path(),
                fixture.definition().caseId(),
                AnalysisMode.INTEGRATED_JAVA
        );

        ArmEvidence controlEvidence = invoke(control, context, source, fixture);
        ArmEvidence candidateEvidence = invoke(candidate, context, source, fixture);
        GeminiFactReusePipelineStage.Trace trace = candidate.lastTraceOrNull();

        return new CaseEvidence(
                fixture.definition().caseId(),
                fixture.definition().expectedClassification().name(),
                fixture.referenceIds(),
                controlEvidence,
                candidateEvidence,
                trace == null ? null : trace.canonicalInputType(),
                trace == null ? null : trace.canonicalClassificationConfidence(),
                trace == null ? null : trace.canonicalClassificationReason(),
                trace == null ? null : trace.canonicalExtractionLatencyMs(),
                trace == null ? null : trace.secondGenerationInvoked()
        );
    }

    private ArmEvidence invoke(
            AnalysisGenerationStage stage,
            AnalysisRequestContext context,
            ObjectContent source,
            OpusGenerationFixtureLoader.FixtureCase fixture
    ) {
        long started = System.nanoTime();
        try {
            AnalysisGenerationResult result = stage.generate(context, source, fixture.references());
            return score(fixture, result, elapsed(started));
        } catch (AnalysisInputRejectedException rejected) {
            return scoreRejected(fixture, rejected, elapsed(started));
        } catch (AnalysisGenerationOutputTruncatedException truncated) {
            return failure(fixture, elapsed(started), "OUTPUT_TRUNCATED",
                    "model output remained truncated after compact retry", true);
        } catch (AnalysisGenerationResponseFormatException format) {
            return failure(fixture, elapsed(started), "RESPONSE_FORMAT",
                    format.getClass().getSimpleName(), false);
        } catch (RuntimeException provider) {
            return failure(fixture, elapsed(started), "PROVIDER_RUNTIME",
                    provider.getClass().getSimpleName(), false);
        }
    }

    private ArmEvidence score(
            OpusGenerationFixtureLoader.FixtureCase fixture,
            AnalysisGenerationResult result,
            long latency
    ) {
        String terraform = result.terraformCode();
        Scoring scoring = scoring(fixture, terraform);
        boolean responseFormatPassed = fixture.definition().generation().terraformExpected() || terraform.isBlank();
        String category = category(
                fixture,
                result.inputClassification(),
                scoring,
                responseFormatPassed,
                result.stopReason()
        );
        return new ArmEvidence(
                modelId,
                result.inputClassification().name(),
                result.classificationConfidence(),
                terraform,
                scoring.resources(),
                scoring.matched(),
                scoring.missing(),
                scoring.forbidden(),
                scoring.validation().valid(),
                scoring.validation().reason(),
                scoring.placeholder(),
                !terraform.isBlank(),
                responseFormatPassed,
                latency,
                result.outputTokens(),
                result.stopReason(),
                result.retryOccurred(),
                category,
                null
        );
    }

    private ArmEvidence scoreRejected(
            OpusGenerationFixtureLoader.FixtureCase fixture,
            AnalysisInputRejectedException rejected,
            long latency
    ) {
        Scoring scoring = scoring(fixture, "");
        String category = category(fixture, rejected.classification(), scoring, true, "");
        return new ArmEvidence(
                modelId,
                rejected.classification().name(),
                rejected.classificationConfidence(),
                "",
                scoring.resources(),
                scoring.matched(),
                scoring.missing(),
                scoring.forbidden(),
                scoring.validation().valid(),
                scoring.validation().reason(),
                false,
                false,
                true,
                latency,
                null,
                "",
                rejected.retryOccurred(),
                category,
                null
        );
    }

    private ArmEvidence failure(
            OpusGenerationFixtureLoader.FixtureCase fixture,
            long latency,
            String category,
            String reason,
            boolean retry
    ) {
        return new ArmEvidence(
                modelId,
                null,
                null,
                "",
                List.of(),
                List.of(),
                fixture.definition().generation().terraformResourceTypes().required(),
                List.of(),
                false,
                reason,
                false,
                false,
                false,
                latency,
                null,
                "",
                retry,
                category,
                reason
        );
    }

    private Scoring scoring(OpusGenerationFixtureLoader.FixtureCase fixture, String terraform) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = RESOURCE.matcher(terraform == null ? "" : terraform);
        while (matcher.find()) found.add(matcher.group(1));

        EvaluationCase.TextExpectation expected = fixture.definition().generation().terraformResourceTypes();
        List<String> matched = expected.required().stream().filter(found::contains).toList();
        List<String> missing = expected.required().stream().filter(value -> !found.contains(value)).toList();
        List<String> forbidden = expected.forbidden().stream().filter(found::contains).toList();
        TerraformDraftValidation validation = fixture.definition().generation().terraformExpected()
                ? validator.validate(terraform)
                : new TerraformDraftValidation(true, "", null);
        boolean placeholder = validation.reason() != null
                && validation.reason().contains("placeholder/example");
        return new Scoring(List.copyOf(found), matched, missing, forbidden, validation, placeholder);
    }

    private String category(
            OpusGenerationFixtureLoader.FixtureCase fixture,
            AnalysisInputClassification observed,
            Scoring scoring,
            boolean responseFormatPassed,
            String stopReason
    ) {
        if ("MAX_TOKENS".equalsIgnoreCase(stopReason)) return "OUTPUT_TRUNCATED";
        if (!responseFormatPassed) return "RESPONSE_FORMAT";
        if (!fixture.definition().expectedClassification().name().equals(observed.name())) {
            return "INPUT_CLASSIFICATION";
        }
        if (!scoring.missing().isEmpty()) return "GENERATION_REQUIRED_RESOURCE_MISSING";
        if (!scoring.forbidden().isEmpty()) return "GENERATION_FORBIDDEN_RESOURCE";
        if (fixture.definition().generation().terraformExpected() && !scoring.validation().valid()) {
            return "TERRAFORM_STRUCTURAL_VALIDATION";
        }
        return "NONE";
    }

    private static ObjectContent source(OpusGenerationFixtureLoader.FixtureCase fixture) {
        return new ObjectContent(
                new ObjectMetadata(
                        "fixture",
                        fixture.definition().input().path(),
                        fixture.definition().input().contentType(),
                        fixture.imageBytes().length,
                        ""
                ),
                fixture.imageBytes()
        );
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private record Scoring(
            List<String> resources,
            List<String> matched,
            List<String> missing,
            List<String> forbidden,
            TerraformDraftValidation validation,
            boolean placeholder
    ) {}

    record CaseEvidence(
            String caseId,
            String expectedClassification,
            List<String> suppliedOrderedReferenceIds,
            ArmEvidence control,
            ArmEvidence candidate,
            String canonicalInputType,
            Double canonicalClassificationConfidence,
            String canonicalClassificationReason,
            Long canonicalExtractionLatencyMs,
            Boolean secondGenerationInvoked
    ) {}

    record ArmEvidence(
            String modelId,
            String observedClassification,
            Double classificationConfidence,
            String generatedTerraform,
            List<String> generatedTerraformResourceTypes,
            List<String> requiredResourceTypesMatched,
            List<String> requiredResourceTypesMissing,
            List<String> forbiddenResourceTypesPresent,
            boolean terraformDraftValidatorPassed,
            String terraformDraftValidatorReason,
            boolean placeholderExampleDetected,
            boolean terraformEmitted,
            boolean responseFormatPassed,
            long latencyMs,
            Integer outputTokens,
            String stopReason,
            boolean compactRetryOccurred,
            String firstFailureCategory,
            String safeFailureReason
    ) {}
}
