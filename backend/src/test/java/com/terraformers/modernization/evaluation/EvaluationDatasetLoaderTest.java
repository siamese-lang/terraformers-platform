package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.evaluation.EvaluationCase.InputClassification;
import com.terraformers.modernization.evaluation.EvaluationCase.ValidationExpectation;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationCase;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluationDatasetLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EvaluationDatasetLoader loader = new EvaluationDatasetLoader(objectMapper);

    @Test
    void loadsVersionedDatasetAndVerifiesAllFixtureHashes() {
        LoadedEvaluationDataset loaded = loader.load(datasetPath());

        assertThat(loaded.dataset().schemaVersion()).isEqualTo("m3-evaluation-v1");
        assertThat(loaded.dataset().datasetVersion()).isEqualTo("terraformers-eval-v1");
        assertThat(loaded.cases()).hasSize(6);

        assertThat(loaded.cases())
                .extracting(loadedCase -> loadedCase.definition().expectedClassification())
                .containsExactlyInAnyOrder(
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        InputClassification.ARCHITECTURE_DIAGRAM,
                        InputClassification.AMBIGUOUS,
                        InputClassification.NON_ARCHITECTURE_IMAGE
                );

        for (LoadedEvaluationCase loadedCase : loaded.cases()) {
            byte[] bytes = loadedCase.inputBytes();
            assertThat(bytes.length).isGreaterThan(12);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
            assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
            assertThat(loadedCase.fixturePath()).isRegularFile();
        }
    }

    @Test
    void loadsRealisticCandidateAndKeepsItsInputsSeparateFromSyntheticControls() {
        LoadedEvaluationDataset candidate = loader.load(
                Path.of("..", "evaluation", "terraformers-realistic-v1", "dataset.json"));
        assertThat(candidate.dataset().schemaVersion()).isEqualTo("m3-evaluation-v1");
        assertThat(candidate.dataset().datasetVersion()).isEqualTo("terraformers-realistic-v1");
        assertThat(candidate.cases()).hasSize(10);
        assertThat(candidate.cases()).filteredOn(c -> c.definition().expectedClassification()
                == InputClassification.ARCHITECTURE_DIAGRAM).hasSize(6);
        assertThat(candidate.cases()).filteredOn(c -> c.definition().expectedClassification()
                == InputClassification.AMBIGUOUS).hasSize(2);
        assertThat(candidate.cases()).filteredOn(c -> c.definition().expectedClassification()
                == InputClassification.NON_ARCHITECTURE_IMAGE).hasSize(2);

        Set<String> syntheticHashes = new HashSet<>();
        for (String version : Set.of("terraformers-eval-v1", "terraformers-eval-holdout-v1")) {
            loader.load(Path.of("..", "evaluation", version, "dataset.json")).cases()
                    .forEach(c -> syntheticHashes.add(c.definition().input().sha256()));
        }
        for (LoadedEvaluationCase loadedCase : candidate.cases()) {
            EvaluationCase definition = loadedCase.definition();
            assertThat(definition.input().contentType()).isEqualTo("image/png");
            assertThat(loadedCase.inputBytes()).startsWith(
                    (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G',
                    (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a);
            assertThat(syntheticHashes).doesNotContain(definition.input().sha256());
            if (definition.expectedClassification() == InputClassification.ARCHITECTURE_DIAGRAM) {
                assertThat(definition.components().required()).isNotEmpty();
                assertThat(definition.relationships().required()).isNotEmpty();
                assertThat(definition.generation().terraformResourceTypes().required()).isNotEmpty();
                assertThat(definition.generation().terraformExpected()).isTrue();
            } else {
                assertThat(definition.generation().terraformExpected()).isFalse();
                assertThat(definition.validation()).isEqualTo(ValidationExpectation.NOT_APPLICABLE);
            }
        }
    }

    @Test
    void positiveCasesContainStageLevelExpectationsAndNegativeCasesForbidTerraformGeneration() {
        LoadedEvaluationDataset loaded = loader.load(datasetPath());

        for (EvaluationCase evaluationCase : loaded.dataset().cases()) {
            if (evaluationCase.expectedClassification() == InputClassification.ARCHITECTURE_DIAGRAM) {
                assertThat(evaluationCase.components().required()).isNotEmpty();
                assertThat(evaluationCase.relationships().required()).isNotEmpty();
                assertThat(evaluationCase.resourceTypes().required()).isNotEmpty();
                assertThat(evaluationCase.retrieval().requiredResourceTypes()).isNotEmpty();
                assertThat(evaluationCase.retrieval().requiredProjectDecisionIds()).isNotEmpty();
                assertThat(evaluationCase.generation().terraformExpected()).isTrue();
                assertThat(evaluationCase.generation().terraformResourceTypes().required()).isNotEmpty();
                assertThat(evaluationCase.validation()).isEqualTo(ValidationExpectation.PASS);
            } else {
                assertThat(evaluationCase.generation().terraformExpected()).isFalse();
                assertThat(evaluationCase.generation().terraformResourceTypes().required()).isEmpty();
                assertThat(evaluationCase.validation()).isEqualTo(ValidationExpectation.NOT_APPLICABLE);
            }
        }
    }

    @Test
    void everyRequiredProjectDecisionExistsInVersionedCorpus() throws Exception {
        LoadedEvaluationDataset loaded = loader.load(datasetPath());
        Set<String> corpusDocumentIds = new HashSet<>();

        for (String line : Files.readAllLines(corpusPath())) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode document = objectMapper.readTree(line);
            String documentId = document.path("documentId").asText();
            if (!documentId.isBlank()) {
                corpusDocumentIds.add(documentId);
            }
        }

        Set<String> requiredProjectDecisions = new HashSet<>();
        for (EvaluationCase evaluationCase : loaded.dataset().cases()) {
            requiredProjectDecisions.addAll(evaluationCase.retrieval().requiredProjectDecisionIds());
        }

        assertThat(requiredProjectDecisions)
                .containsExactlyInAnyOrder(
                        "tfref-v2-sg-relations",
                        "tfref-v2-alb-private-origin",
                        "tfref-v2-aoss-private",
                        "tfref-v2-aoss-access",
                        "tfref-v2-s3-content-metadata"
                );
        assertThat(corpusDocumentIds).containsAll(requiredProjectDecisions);
    }

    private Path datasetPath() {
        return Path.of("..", "evaluation", "terraformers-eval-v1", "dataset.json");
    }

    private Path corpusPath() {
        return Path.of("..", "corpus", "terraformers-reference", "v2", "documents.jsonl");
    }
}
