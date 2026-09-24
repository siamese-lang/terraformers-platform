package com.terraformers.modernization.reference;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SelectedEmbeddingProvider implements EmbeddingProvider {

    private final AnalysisRuntimeProperties properties;
    private final ObjectProvider<BedrockEmbeddingProvider> bedrock;
    private final ObjectProvider<VertexEmbeddingProvider> vertex;

    public SelectedEmbeddingProvider(
            AnalysisRuntimeProperties properties,
            ObjectProvider<BedrockEmbeddingProvider> bedrock,
            ObjectProvider<VertexEmbeddingProvider> vertex
    ) {
        this.properties = properties;
        this.bedrock = bedrock;
        this.vertex = vertex;
    }

    @Override
    public List<Float> embed(String text) {
        return switch (properties.resolvedEmbeddingProvider()) {
            case BEDROCK -> bedrock.getObject().embed(text);
            case VERTEX -> vertex.getObject().embed(text);
            case DISABLED -> throw new IllegalStateException("Embedding provider is disabled");
        };
    }
}
