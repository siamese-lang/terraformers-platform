package com.terraformers.modernization.reference;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class VertexEmbeddingProvider implements EmbeddingProvider {

    private final Client client;
    private final VertexRuntimeProperties properties;

    public VertexEmbeddingProvider(@Lazy Client client, VertexRuntimeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("embedding text must not be blank");
        }
        int expectedDimension = properties.requireEmbeddingDimension();
        EmbedContentConfig config = EmbedContentConfig.builder()
                .taskType("RETRIEVAL_QUERY")
                .outputDimensionality(expectedDimension)
                .build();
        EmbedContentResponse response = client.models.embedContent(
                properties.requireEmbeddingModelId(),
                text.strip(),
                config
        );

        List<ContentEmbedding> embeddings = response.embeddings()
                .orElseThrow(() -> new IllegalStateException("Vertex embedding response has no embeddings"));
        if (embeddings.size() != 1) {
            throw new IllegalStateException("Vertex query embedding response must contain exactly one embedding");
        }
        List<Float> vector = embeddings.get(0).values()
                .orElseThrow(() -> new IllegalStateException("Vertex embedding response has no vector values"));
        if (vector.size() != expectedDimension) {
            throw new IllegalStateException("Vertex embedding vector dimension does not match configured expected dimension");
        }
        if (vector.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalStateException("Vertex embedding response contains a non-finite value");
        }
        return List.copyOf(vector);
    }
}
