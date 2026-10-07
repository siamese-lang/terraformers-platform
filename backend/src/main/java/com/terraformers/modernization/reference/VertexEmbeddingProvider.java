package com.terraformers.modernization.reference;

import com.google.genai.Client;
import com.terraformers.modernization.analysis.AnalysisProviderTimeoutException;
import com.terraformers.modernization.analysis.ProviderFailureClassifier;
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

    private final QueryEmbeddingClient client;
    private final VertexRuntimeProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public VertexEmbeddingProvider(Client client, VertexRuntimeProperties properties) {
        this((model, text, config) -> client.models.embedContent(model, text, config), properties);
    }

    VertexEmbeddingProvider(QueryEmbeddingClient client, VertexRuntimeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @FunctionalInterface
    interface QueryEmbeddingClient {
        EmbedContentResponse embed(String model, String text, EmbedContentConfig config);
    }

    static boolean usesInlineSearchInstruction(String modelId) {
        return "gemini-embedding-2".equals(modelId);
    }

    static String prepareQueryInput(String modelId, String text) {
        String normalized = text.strip();
        if (usesInlineSearchInstruction(modelId)) {
            return "task: search result | query: " + normalized;
        }
        return normalized;
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("embedding text must not be blank");
        }
        int expectedDimension = properties.requireEmbeddingDimension();
        String modelId = properties.requireEmbeddingModelId();
        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder()
                .httpOptions(properties.embeddingHttpOptions())
                .outputDimensionality(expectedDimension);
        if (!usesInlineSearchInstruction(modelId)) {
            configBuilder.taskType("RETRIEVAL_QUERY");
        }
        EmbedContentResponse response;
        try {
            response = client.embed(modelId, prepareQueryInput(modelId, text), configBuilder.build());
        } catch (RuntimeException exception) {
            if (ProviderFailureClassifier.isTimeout(exception)) throw new AnalysisProviderTimeoutException(exception);
            throw exception;
        }

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
