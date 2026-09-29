package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CaseAHoldoutDatasetTest {
    @Test void validatesFrozenCompositionCorpusAndCanonicalSeparation() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path holdoutPath = Path.of("..", "evaluation", "terraformers-eval-holdout-v1", "dataset.json");
        var loaded = new EvaluationDatasetLoader(mapper).load(holdoutPath);
        assertThat(loaded.dataset().datasetVersion()).isEqualTo("terraformers-eval-holdout-v1");
        assertThat(loaded.cases()).hasSize(4);
        assertThat(loaded.dataset().cases()).filteredOn(c -> c.expectedClassification() == EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM).hasSize(2)
                .allSatisfy(c -> { assertThat(c.components().required()).isNotEmpty(); assertThat(c.relationships().required()).isNotEmpty(); assertThat(c.retrieval().requiredResourceTypes()).isNotEmpty(); assertThat(c.retrieval().requiredProjectDecisionIds()).isNotEmpty(); assertThat(c.generation().terraformResourceTypes().required()).isNotEmpty(); assertThat(c.validation()).isEqualTo(EvaluationCase.ValidationExpectation.PASS); });
        assertThat(loaded.dataset().cases()).filteredOn(c -> c.expectedClassification() == EvaluationCase.InputClassification.AMBIGUOUS).hasSize(1);
        assertThat(loaded.dataset().cases()).filteredOn(c -> c.expectedClassification() == EvaluationCase.InputClassification.NON_ARCHITECTURE_IMAGE).hasSize(1);
        assertThat(loaded.dataset().cases()).filteredOn(c -> c.expectedClassification() != EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM)
                .allSatisfy(c -> { assertThat(c.generation().terraformExpected()).isFalse(); assertThat(c.validation()).isEqualTo(EvaluationCase.ValidationExpectation.NOT_APPLICABLE); assertThat(c.retrieval().requiredResourceTypes()).isEmpty(); });
        for (var item : loaded.cases()) { byte[] bytes = item.inputBytes(); assertThat(bytes).isNotEmpty(); assertThat(new String(bytes, 0, 4)).isEqualTo("RIFF"); assertThat(new String(bytes, 8, 4)).isEqualTo("WEBP"); }
        String corpus = Files.readString(Path.of("..", "corpus", "terraformers-reference", "v3", "documents.jsonl"));
        loaded.dataset().cases().stream().flatMap(c -> c.retrieval().requiredProjectDecisionIds().stream()).forEach(id -> assertThat(corpus).contains("\"documentId\": \"" + id + "\""));
        var canonical = new EvaluationDatasetLoader(mapper).load(Path.of("..", "evaluation", "terraformers-eval-v1", "dataset.json"));
        Set<String> ids = canonical.dataset().cases().stream().map(EvaluationCase::caseId).collect(Collectors.toSet());
        Set<String> hashes = canonical.dataset().cases().stream().map(c -> c.input().sha256()).collect(Collectors.toSet());
        assertThat(loaded.dataset().cases()).noneMatch(c -> ids.contains(c.caseId()) || hashes.contains(c.input().sha256()));
    }
}
