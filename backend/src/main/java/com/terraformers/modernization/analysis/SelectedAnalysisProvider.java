package com.terraformers.modernization.analysis;

import com.terraformers.modernization.analysis.bedrock.BedrockAnalysisProvider;
import com.terraformers.modernization.analysis.vertex.VertexAnalysisProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SelectedAnalysisProvider implements AnalysisProvider {

    private final AnalysisRuntimeProperties properties;
    private final StubAnalysisProvider stub;
    private final ObjectProvider<BedrockAnalysisProvider> bedrock;
    private final ObjectProvider<VertexAnalysisProvider> vertex;

    public SelectedAnalysisProvider(
            AnalysisRuntimeProperties properties,
            StubAnalysisProvider stub,
            ObjectProvider<BedrockAnalysisProvider> bedrock,
            ObjectProvider<VertexAnalysisProvider> vertex
    ) {
        this.properties = properties;
        this.stub = stub;
        this.bedrock = bedrock;
        this.vertex = vertex;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequestContext context) {
        return switch (properties.resolvedProvider()) {
            case STUB -> stub.analyze(context);
            case BEDROCK -> bedrock.getObject().analyze(context);
            case VERTEX -> vertex.getObject().analyze(context);
        };
    }
}
