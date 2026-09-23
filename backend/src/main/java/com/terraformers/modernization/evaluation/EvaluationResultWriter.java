package com.terraformers.modernization.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class EvaluationResultWriter {

    private final ObjectMapper objectMapper;

    public EvaluationResultWriter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Path write(Path outputFile, EvaluationRunResult result) {
        Path normalized = Objects.requireNonNull(outputFile, "outputFile").toAbsolutePath().normalize();
        Objects.requireNonNull(result, "result");
        try {
            Path parent = normalized.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(normalized.toFile(), result);
            return normalized;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write evaluation result: " + normalized, exception);
        }
    }
}
