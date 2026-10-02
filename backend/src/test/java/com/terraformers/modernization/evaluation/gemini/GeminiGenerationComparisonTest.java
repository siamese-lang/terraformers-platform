package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.*;
import com.terraformers.modernization.analysis.vertex.VertexOutputTruncatedException;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeminiGenerationComparisonTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root = Path.of("..").toAbsolutePath().normalize();
    private final Path manifest = root.resolve("evaluation/opus-generation-v1/manifest.json");

    private OpusGenerationFixtureLoader.LoadedFixture fixture() {
        return new OpusGenerationFixtureLoader(mapper).load(manifest,
                root.resolve("evaluation/terraformers-eval-v1/dataset.json"),
                root.resolve("corpus/terraformers-reference/v3/documents.jsonl"));
    }

    @Test void loadsSixFrozenCasesInExactReferenceOrderWithoutModifyingManifest() throws Exception {
        byte[] before = Files.readAllBytes(manifest);
        var loaded = fixture();
        assertThat(loaded.cases()).hasSize(6);
        loaded.cases().forEach(c -> assertThat(c.references()).extracting(r -> r.id())
                .containsExactlyElementsOf(c.referenceIds()));
        assertThat(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(manifest)))
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(before));
    }

    @Test void identitiesAndArtifactAreFrozenAndBothArmsReceiveSamePrompt() {
        List<String> hashes = new ArrayList<>();
        AnalysisGenerationStage fake = (context, source, refs) -> {
            hashes.add(source.metadata().key() + refs.stream().map(r -> r.id()).toList());
            return passing(fixture().cases().stream().filter(c -> c.definition().input().path().equals(source.metadata().key())).findFirst().orElseThrow());
        };
        var artifact = GeminiGenerationComparisonLauncher.run(fixture(),
                new GeminiGenerationComparisonRunner(fake, fake), "run", "source");
        assertThat(GeminiGenerationComparisonRunner.CONTROL_MODEL_ID).isEqualTo("gemini-3.8-flash");
        assertThat(GeminiGenerationComparisonRunner.CANDIDATE_MODEL_ID).isEqualTo("gemini-3.1-pro-preview");
        assertThat(artifact.fixtureSchemaVersion()).isEqualTo("opus-generation-fixture-v1");
        assertThat(artifact.fixtureManifestSha256()).hasSize(64);
        assertThat(artifact.controlModelId()).isEqualTo("gemini-3.8-flash");
        assertThat(artifact.candidateModelId()).isEqualTo("gemini-3.1-pro-preview");
        artifact.cases().forEach(c -> {
            assertThat(c.control().renderedPromptSha256()).isEqualTo(c.renderedPromptSha256());
            assertThat(c.candidate().renderedPromptSha256()).isEqualTo(c.renderedPromptSha256());
        });
        assertThat(hashes).hasSize(12);
    }

    @Test void preservesIndependentPassAndFailureEvidenceInEitherDirection() {
        var c = fixture().cases().get(0);
        AnalysisGenerationStage pass = (x, y, z) -> passing(c);
        AnalysisGenerationStage fail = (x, y, z) -> new AnalysisGenerationResult("fake",
                AnalysisInputClassification.NON_ARCHITECTURE_IMAGE, .9, "", "", List.of(), List.of(), List.of(), "STOP", 3, false);
        var candidatePass = new GeminiGenerationComparisonRunner(fail, pass).evaluate(c);
        assertThat(candidatePass.control().firstFailureCategory()).isEqualTo("INPUT_CLASSIFICATION");
        assertThat(candidatePass.candidate().firstFailureCategory()).isEqualTo("NONE");
        var controlPass = new GeminiGenerationComparisonRunner(pass, fail).evaluate(c);
        assertThat(controlPass.control().firstFailureCategory()).isEqualTo("NONE");
        assertThat(controlPass.candidate().firstFailureCategory()).isEqualTo("INPUT_CLASSIFICATION");
    }

    @Test void providerFailureInOneArmDoesNotAbortOtherArmOrLaterCases() {
        AtomicInteger controlCalls = new AtomicInteger();
        AnalysisGenerationStage flaky = (x, y, z) -> { if (controlCalls.incrementAndGet() == 1) throw new IllegalStateException("quota"); return passing(caseFor(y.metadata().key())); };
        AnalysisGenerationStage pass = (x, y, z) -> passing(caseFor(y.metadata().key()));
        var artifact = GeminiGenerationComparisonLauncher.run(fixture(), new GeminiGenerationComparisonRunner(flaky, pass), "run", "source");
        assertThat(artifact.cases()).hasSize(6);
        assertThat(artifact.cases().get(0).control().firstFailureCategory()).isEqualTo("PROVIDER_RUNTIME");
        assertThat(artifact.cases().get(0).candidate().firstFailureCategory()).isEqualTo("NONE");
        assertThat(artifact.cases().get(1).control().firstFailureCategory()).isEqualTo("NONE");
    }

    @Test void appliesClassificationMissingForbiddenAndAllowsPlaceholderCommentInUsableDraft() {
        var c = fixture().cases().get(0);
        assertThat(run(c, AnalysisInputClassification.NON_ARCHITECTURE_IMAGE, "").firstFailureCategory()).isEqualTo("INPUT_CLASSIFICATION");
        assertThat(run(c, AnalysisInputClassification.ARCHITECTURE_DIAGRAM, "resource \"aws_vpc\" \"x\" {}").firstFailureCategory())
                .isEqualTo("GENERATION_REQUIRED_RESOURCE_MISSING");
        String required = terraform(c);
        assertThat(run(c, AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                required + "\nresource \"aws_cloudfront_distribution\" \"bad\" {}").firstFailureCategory())
                .isEqualTo("GENERATION_FORBIDDEN_RESOURCE");
        var placeholderDraft = run(
                c, AnalysisInputClassification.ARCHITECTURE_DIAGRAM, required + "\n# placeholder");
        assertThat(placeholderDraft.firstFailureCategory()).isEqualTo("NONE");
        assertThat(placeholderDraft.placeholderExampleDetected()).isFalse();
    }

    @Test void recordsNegativeTerraformAndOutputTruncation() {
        var negative = fixture().cases().get(4);
        var emitted = run(negative, AnalysisInputClassification.AMBIGUOUS, "resource \"aws_vpc\" \"bad\" {}");
        assertThat(emitted.terraformEmitted()).isTrue();
        assertThat(emitted.firstFailureCategory()).isEqualTo("RESPONSE_FORMAT");
        AnalysisGenerationStage truncated = (x, y, z) -> { throw new VertexOutputTruncatedException(8192); };
        var evidence = new GeminiGenerationComparisonRunner(truncated, truncated).evaluate(fixture().cases().get(0));
        assertThat(evidence.control().firstFailureCategory()).isEqualTo("OUTPUT_TRUNCATED");
        assertThat(evidence.control().compactRetryOccurred()).isTrue();
    }

    @Test void rejectsAnyRuntimeIdentityDrift() {
        assertThatThrownBy(() -> GeminiGenerationComparisonLauncher.requireIdentity("us-central1",
                "gemini-3.8-flash", "gemini-3.1-pro-preview", 8192)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeminiGenerationComparisonLauncher.requireIdentity("global",
                "other", "gemini-3.1-pro-preview", 8192)).isInstanceOf(IllegalArgumentException.class);
    }

    private GeminiGenerationComparisonRunner.ArmEvidence run(OpusGenerationFixtureLoader.FixtureCase c,
                                                               AnalysisInputClassification classification, String terraform) {
        AnalysisGenerationStage fake = (x, y, z) -> new AnalysisGenerationResult("fake", classification, .9,
                terraform, "", List.of(), List.of(), List.of(), "STOP", 5, false);
        return new GeminiGenerationComparisonRunner(fake, fake).evaluate(c).control();
    }
    private AnalysisGenerationResult passing(OpusGenerationFixtureLoader.FixtureCase c) {
        return new AnalysisGenerationResult("fake", AnalysisInputClassification.valueOf(c.definition().expectedClassification().name()),
                .9, c.definition().generation().terraformExpected() ? terraform(c) : "", "", List.of(), List.of(), List.of(), "STOP", 5, false);
    }
    private String terraform(OpusGenerationFixtureLoader.FixtureCase c) {
        StringBuilder out = new StringBuilder();
        c.definition().generation().terraformResourceTypes().required().forEach(type -> out.append("resource \"").append(type).append("\" \"test\" { name = \"test\" }\n"));
        return out.toString();
    }
    private OpusGenerationFixtureLoader.FixtureCase caseFor(String path) {
        return fixture().cases().stream().filter(c -> c.definition().input().path().equals(path)).findFirst().orElseThrow();
    }
}
