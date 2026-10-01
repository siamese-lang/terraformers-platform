package com.terraformers.modernization.analysis.vertex;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import com.terraformers.modernization.reference.VertexEmbeddingProvider;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;

class VertexFinalClientInjectionTest {

    @Test
    void finalGenAiClientIsNotInjectedThroughLazyParameterProxy() {
        assertThat(Modifier.isFinal(Client.class.getModifiers())).isTrue();

        for (Class<?> component : List.of(
                VertexArchitectureFactsExtractor.class,
                VertexEmbeddingProvider.class,
                VertexGenerationStage.class
        )) {
            Parameter clientParameter = List.of(component.getDeclaredConstructors()[0].getParameters()).stream()
                    .filter(parameter -> parameter.getType().equals(Client.class))
                    .findFirst()
                    .orElseThrow();

            assertThat(clientParameter.isAnnotationPresent(Lazy.class))
                    .as("%s must not lazy-proxy final com.google.genai.Client", component.getSimpleName())
                    .isFalse();
        }
    }
}
