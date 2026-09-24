package com.terraformers.modernization.analysis.vertex;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.analysis.vertex")
public class VertexRuntimeProperties {

    private String projectId;
    private String location = "global";
    private String generationModelId = "gemini-3.8-flash";
    private String embeddingModelId = "gemini-embedding-001";
    private int embeddingDimension = 1024;
    private int maxOutputTokens = 8192;

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

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " must be set when Vertex AI is enabled");
        }
        return value.strip();
    }
}
