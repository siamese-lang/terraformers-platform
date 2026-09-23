package com.terraformers.modernization.reference;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Primary
public class SelectedEmbeddingProvider implements EmbeddingProvider {
    private final AnalysisRuntimeProperties properties;
    private final ObjectProvider<BedrockEmbeddingProvider> bedrock;

    public SelectedEmbeddingProvider(AnalysisRuntimeProperties properties, ObjectProvider<BedrockEmbeddingProvider> bedrock) {
        this.properties = properties;
        this.bedrock = bedrock;
    }

    @Override
    public List<Float> embed(String text) {
        return switch (properties.resolvedEmbeddingProvider()) {
            case BEDROCK -> bedrock.getObject().embed(text);
            case DISABLED -> throw new IllegalStateException("Embedding provider is disabled");
        };
    }
}
