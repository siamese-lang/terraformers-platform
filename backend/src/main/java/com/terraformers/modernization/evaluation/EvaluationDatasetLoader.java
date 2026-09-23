package com.terraformers.modernization.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public class EvaluationDatasetLoader {

    private final ObjectMapper objectMapper;

    public EvaluationDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public LoadedEvaluationDataset load(Path datasetFile) {
        Path normalizedDatasetFile = Objects.requireNonNull(datasetFile, "datasetFile")
                .toAbsolutePath()
                .normalize();
        Path datasetRoot = normalizedDatasetFile.getParent();
        if (datasetRoot == null) {
            throw new IllegalArgumentException("datasetFile must have a parent directory");
        }

        try {
            EvaluationDataset dataset = objectMapper.readValue(
                    Files.readString(normalizedDatasetFile),
                    EvaluationDataset.class
            );
            List<LoadedEvaluationCase> loadedCases = dataset.cases().stream()
                    .map(evaluationCase -> loadCase(datasetRoot, evaluationCase))
                    .toList();
            return new LoadedEvaluationDataset(dataset, loadedCases);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load evaluation dataset: " + normalizedDatasetFile, exception);
        }
    }

    private LoadedEvaluationCase loadCase(Path datasetRoot, EvaluationCase evaluationCase) {
        Path fixturePath = resolveFixture(datasetRoot, evaluationCase.input().path());
        try {
            byte[] inputBytes = Files.readAllBytes(fixturePath);
            if (inputBytes.length == 0) {
                throw new IllegalStateException("evaluation fixture is empty: " + fixturePath);
            }
            String actualSha256 = sha256(inputBytes);
            String expectedSha256 = evaluationCase.input().sha256().toLowerCase();
            if (!actualSha256.equals(expectedSha256)) {
                throw new IllegalStateException(
                        "evaluation fixture SHA-256 mismatch for " + evaluationCase.caseId()
                                + ": expected=" + expectedSha256 + " actual=" + actualSha256
                );
            }
            return new LoadedEvaluationCase(evaluationCase, fixturePath, inputBytes);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "failed to read evaluation fixture for " + evaluationCase.caseId() + ": " + fixturePath,
                    exception
            );
        }
    }

    private Path resolveFixture(Path datasetRoot, String relativeFixturePath) {
        Path relative = Path.of(relativeFixturePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("evaluation fixture path must be relative: " + relativeFixturePath);
        }
        Path resolved = datasetRoot.resolve(relative).normalize();
        if (!resolved.startsWith(datasetRoot)) {
            throw new IllegalArgumentException("evaluation fixture path escapes dataset root: " + relativeFixturePath);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalStateException("evaluation fixture does not exist: " + resolved);
        }
        return resolved;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record LoadedEvaluationDataset(
            EvaluationDataset dataset,
            List<LoadedEvaluationCase> cases
    ) {
        public LoadedEvaluationDataset {
            dataset = Objects.requireNonNull(dataset, "dataset");
            cases = cases == null ? List.of() : List.copyOf(cases);
            if (cases.size() != dataset.cases().size()) {
                throw new IllegalArgumentException("loaded case count must match dataset case count");
            }
        }
    }

    public record LoadedEvaluationCase(
            EvaluationCase definition,
            Path fixturePath,
            byte[] inputBytes
    ) {
        public LoadedEvaluationCase {
            definition = Objects.requireNonNull(definition, "definition");
            fixturePath = Objects.requireNonNull(fixturePath, "fixturePath").toAbsolutePath().normalize();
            inputBytes = Objects.requireNonNull(inputBytes, "inputBytes").clone();
        }

        @Override
        public byte[] inputBytes() {
            return inputBytes.clone();
        }
    }
}
