package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.evaluation.opus.OpusGenerationFixtureLoader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiThinkingComparisonTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root = Path.of("..").toAbsolutePath().normalize();

    @Test
    void freezesSameModelMediumVersusLowIdentityOverSixCases() {
        var fixture = fixture();
        AnalysisGenerationStage fake = (context, source, refs) -> passing(caseFor(fixture, source.metadata().key()));

        var artifact = GeminiThinkingComparisonLauncher.run(
                fixture,
                new GeminiGenerationComparisonRunner(
                        fake,
                        fake,
                        GeminiThinkingComparisonLauncher.MODEL_ID,
                        GeminiThinkingComparisonLauncher.MODEL_ID
                ),
                "run",
                "source"
        );

        assertThat(artifact.schemaVersion()).isEqualTo("gemini-thinking-comparison-v1");
        assertThat(artifact.modelId()).isEqualTo("gemini-3.8-flash");
        assertThat(artifact.controlThinkingLevel()).isEqualTo("MEDIUM");
        assertThat(artifact.candidateThinkingLevel()).isEqualTo("LOW");
        assertThat(artifact.maxOutputTokens()).isEqualTo(8192);
        assertThat(artifact.caseCount()).isEqualTo(6);
        assertThat(artifact.controlMeetsFrozenAcceptance()).isTrue();
        assertThat(artifact.candidateMeetsFrozenAcceptance()).isTrue();
        assertThat(artifact.cases()).hasSize(6);
        artifact.cases().forEach(evidence -> {
            assertThat(evidence.control().modelId()).isEqualTo("gemini-3.8-flash");
            assertThat(evidence.candidate().modelId()).isEqualTo("gemini-3.8-flash");
            assertThat(evidence.control().renderedPromptSha256()).isEqualTo(evidence.renderedPromptSha256());
            assertThat(evidence.candidate().renderedPromptSha256()).isEqualTo(evidence.renderedPromptSha256());
        });
    }

    @Test
    void rejectsThinkingComparisonIdentityDrift() {
        assertThatThrownBy(() -> GeminiThinkingComparisonLauncher.requireIdentity(
                "us-central1", "gemini-3.8-flash", "MEDIUM", "LOW", 8192))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeminiThinkingComparisonLauncher.requireIdentity(
                "global", "other", "MEDIUM", "LOW", 8192))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeminiThinkingComparisonLauncher.requireIdentity(
                "global", "gemini-3.8-flash", "LOW", "MEDIUM", 8192))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void existingModelComparisonRunnerKeepsFrozenDefaultArmIds() {
        var fixtureCase = fixture().cases().get(0);
        AnalysisGenerationStage fake = (context, source, refs) -> passing(fixtureCase);

        var evidence = new GeminiGenerationComparisonRunner(fake, fake).evaluate(fixtureCase);

        assertThat(evidence.control().modelId())
                .isEqualTo(GeminiGenerationComparisonRunner.CONTROL_MODEL_ID);
        assertThat(evidence.candidate().modelId())
                .isEqualTo(GeminiGenerationComparisonRunner.CANDIDATE_MODEL_ID);
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

    private AnalysisGenerationResult passing(OpusGenerationFixtureLoader.FixtureCase c) {
        String terraform = c.definition().generation().terraformExpected() ? terraform(c) : "";
        return new AnalysisGenerationResult(
                "fake",
                AnalysisInputClassification.valueOf(c.definition().expectedClassification().name()),
                0.9,
                terraform,
                "",
                List.of(),
                List.of(),
                List.of(),
                "STOP",
                5,
                false
        );
    }

    private String terraform(OpusGenerationFixtureLoader.FixtureCase c) {
        StringBuilder out = new StringBuilder();
        c.definition().generation().terraformResourceTypes().required()
                .forEach(type -> out.append("resource \"")
                        .append(type)
                        .append("\" \"test\" { name = \"test\" }\n"));
        return out.toString();
    }
}
