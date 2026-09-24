package com.terraformers.modernization.analysis.vertex;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class VertexRuntimeConfiguration {

    @Bean
    @Lazy
    Client vertexGenAiClient(VertexRuntimeProperties properties) {
        return Client.builder()
                .project(properties.requireProjectId())
                .location(properties.requireLocation())
                .vertexAI(true)
                .httpOptions(HttpOptions.builder().apiVersion("v1").build())
                .build();
    }
}
