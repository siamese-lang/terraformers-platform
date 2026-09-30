package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiFactReuseComparisonTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root = Path.of("..").toAbsolutePath().normalize();

    @Test
    void sixCaseFakeRunAcceptsMatchingCanonicalClassificationsAndSkipsNegativeSecondGeneration() {
        var fixture = fixture();

        AnalysisGenerationStage control = (context, source, references) -> {
            var c = caseFor(fixture, source.metadata().key());
            AnalysisInputClassification expected = AnalysisInputClassification.valueOf(
                    c.definition().expectedClassification().name());
            if (expected != AnalysisInputClassification.ARCHITECTURE_DIAGRAM) {
                throw new AnalysisInputRejectedException(expected, 0.95, false, null);
            }
            return passing(c);
        };

        GeminiFactReusePipelineStage candidate = new GeminiFactReusePipelineStage(
                source -> {
                    var c = caseFor(fixture, source.metadata().key());
                    AnalysisInputClassification classification = AnalysisInputClassification.valueOf(
                            c.definition().expectedClassification().name());
                    if (classification == AnalysisInputClassification.ARCHITECTURE_DIAGRAM) {
                        return new GeminiCanonicalEnvelope(
                                classification,
                                0.96,
                                "fixture canonical classification",
                                "fixture architecture",
                                List.of("component"),
                                List.of("component -> dependency"),
                                c.definition().generation().terraformResourceTypes().required()
                        );
                    }
                    return new GeminiCanonicalEnvelope(
                            classification,
                            0.96,
                            "fixture canonical classification",
                            "",
                            List.of(),
                            List.of(),
                            List.of()
                    );
                },
                (context, envelope, references) -> passing(caseById(fixture, context.correlationId()))
        );

        GeminiFactReuseComparisonRunner runner =
                new GeminiFactReuseComparisonRunner(control, candidate, "gemini-3.8-flash");

        var cases = fixture.cases().stream().map(runner::evaluate).toList();

        assertThat(GeminiFactReuseComparisonLauncher.armAccepted(cases, true)).isTrue();
        assertThat(GeminiFactReuseComparisonLauncher.armAccepted(cases, false)).isTrue();
        assertThat(GeminiFactReuseComparisonLauncher.canonicalClassificationAccepted(cases)).isTrue();
        assertThat(cases).filteredOn(c -> !"ARCHITECTURE_DIAGRAM".equals(c.expectedClassification()))
                .allSatisfy(c -> assertThat(c.secondGenerationInvoked()).isFalse());
        assertThat(cases).filteredOn(c -> "ARCHITECTURE_DIAGRAM".equals(c.expectedClassification()))
                .allSatisfy(c -> assertThat(c.secondGenerationInvoked()).isTrue());
    }

    @Test
    void artifactMapperSerializesNestedTelemetryInstantsAsIsoStrings() throws Exception {
        Instant fixed = Instant.parse("2026-09-30T18:00:00Z");
        ObjectMapper artifactMapper = GeminiFactReuseComparisonLauncher.artifactMapper();
        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(
                artifactMapper, Clock.fixed(fixed, ZoneOffset.UTC));

        var scope = telemetry.start(
                "case",
                GeminiLatencyTelemetry.Arm.CONTROL,
                GeminiLatencyTelemetry.Phase.FACT_EXTRACTION,
                new GeminiLatencyTelemetry.RequestIdentity("gemini-3.8-flash", "global", "LOW", 800),
                GeminiLatencyTelemetry.PayloadShape.of("IMAGE_AND_TEXT", 3, "", List.of()));
        telemetry.callStart();
        scope.close(null);

        String json = artifactMapper.writeValueAsString(
                telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CONTROL));
        var tree = artifactMapper.readTree(json);

        assertThat(tree.get(0).get("startedAt").asText()).isEqualTo(fixed.toString());
        assertThat(tree.get(0).get("endedAt").asText()).isEqualTo(fixed.toString());
        assertThat(tree.get(0).get("requests").get(0).get("requestStartedAt").asText())
                .isEqualTo(fixed.toString());
        assertThat(tree.get(0).get("requests").get(0).get("responseCompletedAt").asText())
                .isEqualTo(fixed.toString());
    }

    @Test
    void absentOrBlankSelectorPreservesAllSixCasesInFixtureOrder() {
        var fixture = fixture();
        List<String> expected = fixture.cases().stream().map(c -> c.definition().caseId()).toList();

        assertThat(GeminiFactReuseComparisonLauncher.selectCases(fixture.cases(), null))
                .extracting(c -> c.definition().caseId())
                .containsExactlyElementsOf(expected);
        assertThat(GeminiFactReuseComparisonLauncher.selectCases(fixture.cases(), "  "))
                .extracting(c -> c.definition().caseId())
                .containsExactlyElementsOf(expected);
        assertThat(expected).hasSize(6);
    }

    @Test
    void frozenDiagnosticSelectorReturnsOnlyAnomalousCasesInFrozenOrderWithoutMutation() {
        var fixture = fixture();
        List<OpusGenerationFixtureLoader.FixtureCase> original = List.copyOf(fixture.cases());

        var selected = GeminiFactReuseComparisonLauncher.selectCases(
                fixture.cases(), String.join(",", GeminiFactReuseComparisonLauncher.DIAGNOSTIC_CASE_IDS));

        assertThat(selected).extracting(c -> c.definition().caseId())
                .containsExactly("arch-cloudfront-private-alb", "arch-private-aoss");
        assertThat(selected.get(0)).isSameAs(caseById(fixture, "arch-cloudfront-private-alb"));
        assertThat(selected.get(1)).isSameAs(caseById(fixture, "arch-private-aoss"));
        assertThat(fixture.cases()).containsExactlyElementsOf(original);
    }

    @Test
    void targetedSelectorRejectsUnknownCaseBeforeEvaluation() {
        var fixture = fixture();

        assertThatThrownBy(() -> GeminiFactReuseComparisonLauncher.selectCases(
                fixture.cases(), "arch-cloudfront-private-alb,unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown FACT_REUSE case IDs")
                .hasMessageContaining("unknown");
    }

    @Test
    void targetedSelectorRejectsDuplicateCaseBeforeEvaluation() {
        var fixture = fixture();

        assertThatThrownBy(() -> GeminiFactReuseComparisonLauncher.selectCases(
                fixture.cases(), "arch-private-aoss,arch-private-aoss"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain duplicates");
    }

    @Test
    void rejectsFactReuseRuntimeIdentityDrift() {
        assertThatThrownBy(() -> GeminiFactReuseComparisonLauncher.requireIdentity(
                "us-central1", "gemini-3.8-flash", 8192))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeminiFactReuseComparisonLauncher.requireIdentity(
                "global", "other", 8192))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeminiFactReuseComparisonLauncher.requireIdentity(
                "global", "gemini-3.8-flash", 4096))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OpusGenerationFixtureLoader.LoadedFixture fixture() {
        return new OpusGenerationFixtureLoader(mapper).load(
                root.resolve("evaluation/opus-generation-v1/manifest.json"),
                root.resolve("evaluation/terraformers-eval-v1/dataset.json"),
                root.resolve("corpus/terraformers-reference/v3/documents.jsonl")
        );
    }

    private OpusGenerationFixtureLoader.FixtureCase caseFor(
            OpusGenerationFixtureLoader.LoadedFixture fixture,
            String path
    ) {
        return fixture.cases().stream()
                .filter(c -> c.definition().input().path().equals(path))
                .findFirst()
                .orElseThrow();
    }

    private OpusGenerationFixtureLoader.FixtureCase caseById(
            OpusGenerationFixtureLoader.LoadedFixture fixture,
            String id
    ) {
        return fixture.cases().stream()
                .filter(c -> c.definition().caseId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private AnalysisGenerationResult passing(OpusGenerationFixtureLoader.FixtureCase c) {
        String terraform = terraform(c);
        return new AnalysisGenerationResult(
                "fake",
                AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                0.95,
                terraform,
                "fixture architecture",
                List.of("component"),
                List.of("relationship"),
                List.of(),
                "STOP",
                100,
                false
        );
    }

    private String terraform(OpusGenerationFixtureLoader.FixtureCase c) {
        StringBuilder out = new StringBuilder();
        c.definition().generation().terraformResourceTypes().required().forEach(
                type -> out.append("resource \"").append(type)
                        .append("\" \"test\" { name = \"test\" }\n"));
        return out.toString();
    }
}
