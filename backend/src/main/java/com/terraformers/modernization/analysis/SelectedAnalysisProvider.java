package com.terraformers.modernization.analysis;

import com.terraformers.modernization.analysis.bedrock.BedrockAnalysisProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SelectedAnalysisProvider implements AnalysisProvider {
    private final AnalysisRuntimeProperties properties;
    private final StubAnalysisProvider stub;
    private final ObjectProvider<BedrockAnalysisProvider> bedrock;

    public SelectedAnalysisProvider(AnalysisRuntimeProperties properties, StubAnalysisProvider stub,
                                    ObjectProvider<BedrockAnalysisProvider> bedrock) {
        this.properties = properties;
        this.stub = stub;
        this.bedrock = bedrock;
    }

    @Override
    public AnalysisResult analyze(AnalysisRequestContext context) {
        return switch (properties.resolvedProvider()) {
            case STUB -> stub.analyze(context);
            case BEDROCK -> bedrock.getObject().analyze(context);
        };
    }
}
