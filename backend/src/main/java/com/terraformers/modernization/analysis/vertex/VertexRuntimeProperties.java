package com.terraformers.modernization.analysis.vertex;

import com.google.genai.types.ThinkingLevel;
import java.util.Locale;
import java.time.Duration;
import com.google.genai.types.HttpOptions;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.analysis.vertex")
public class VertexRuntimeProperties {

    private String projectId;
    private String location = "global";
    private String generationModelId = "gemini-3.8-flash";
    private String embeddingModelId = "gemini-embedding-001";
    private int embeddingDimension = 1024;
    private int maxOutputTokens = 8192;
    private String generationThinkingLevel;
    private Duration factsTimeout = Duration.ofSeconds(370);
    private Duration generationTimeout = Duration.ofSeconds(220);
    private Duration embeddingTimeout = Duration.ofSeconds(10);

    public Duration getFactsTimeout() { return factsTimeout; }
    public void setFactsTimeout(Duration value) { factsTimeout = value; }
    public Duration getGenerationTimeout() { return generationTimeout; }
    public void setGenerationTimeout(Duration value) { generationTimeout = value; }
    public Duration getEmbeddingTimeout() { return embeddingTimeout; }
    public void setEmbeddingTimeout(Duration value) { embeddingTimeout = value; }

    public HttpOptions factsHttpOptions() { return timeoutOptions(factsTimeout, 370, "facts-timeout"); }
    public HttpOptions generationHttpOptions() { return timeoutOptions(generationTimeout, 220, "generation-timeout"); }
    public HttpOptions embeddingHttpOptions() { return timeoutOptions(embeddingTimeout, 10, "embedding-timeout"); }

    private HttpOptions timeoutOptions(Duration duration, int ceilingSeconds, String property) {
        if (duration == null || duration.compareTo(Duration.ofMillis(1)) < 0
                || duration.compareTo(Duration.ofSeconds(ceilingSeconds)) > 0) {
            throw new IllegalStateException("terraformers.analysis.vertex." + property
                    + " must be between 1ms and " + ceilingSeconds + "s");
        }
        return HttpOptions.builder().timeout(Math.toIntExact(duration.toMillis())).build();
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getGenerationModelId() { return generationModelId; }
    public void setGenerationModelId(String generationModelId) { this.generationModelId = generationModelId; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(String embeddingModelId) { this.embeddingModelId = embeddingModelId; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public String getGenerationThinkingLevel() { return generationThinkingLevel; }
    public void setGenerationThinkingLevel(String generationThinkingLevel) {
        this.generationThinkingLevel = generationThinkingLevel;
    }

    public String requireProjectId() {
        return requireText(projectId, "terraformers.analysis.vertex.project-id");
    }

    public String requireLocation() {
        return requireText(location, "terraformers.analysis.vertex.location");
    }

    public String requireGenerationModelId() {
        return requireText(generationModelId, "terraformers.analysis.vertex.generation-model-id");
    }

    public String requireEmbeddingModelId() {
        return requireText(embeddingModelId, "terraformers.analysis.vertex.embedding-model-id");
    }

    public int requireEmbeddingDimension() {
        if (embeddingDimension <= 0) {
            throw new IllegalStateException("terraformers.analysis.vertex.embedding-dimension must be positive");
        }
        return embeddingDimension;
    }

    public int requireMaxOutputTokens() {
        if (maxOutputTokens <= 0) {
            throw new IllegalStateException("terraformers.analysis.vertex.max-output-tokens must be positive");
        }
        return maxOutputTokens;
    }

    public Optional<ThinkingLevel.Known> resolvedGenerationThinkingLevel() {
        if (generationThinkingLevel == null || generationThinkingLevel.isBlank()) {
            return Optional.empty();
        }
        String normalized = generationThinkingLevel.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW" -> Optional.of(ThinkingLevel.Known.LOW);
            case "MEDIUM" -> Optional.of(ThinkingLevel.Known.MEDIUM);
            case "HIGH" -> Optional.of(ThinkingLevel.Known.HIGH);
            default -> throw new IllegalStateException(
                    "terraformers.analysis.vertex.generation-thinking-level must be LOW, MEDIUM, or HIGH");
        };
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " must be set when Vertex AI is enabled");
        }
        return value.strip();
    }
}
