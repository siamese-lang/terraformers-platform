package com.terraformers.modernization.analysis.vertex;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class VertexRuntimeConfiguration {

    // The 1.72.0 interceptor still sleeps on retryable statuses at attempts=1 unless this list is empty.
    static HttpOptions clientHttpOptions() {
        return HttpOptions.builder().apiVersion("v1")
                .retryOptions(HttpRetryOptions.builder().attempts(1).httpStatusCodes(List.of()).build())
                .build();
    }

    @Bean
    @Lazy
    Client vertexGenAiClient(VertexRuntimeProperties properties) {
        return Client.builder()
                .project(properties.requireProjectId())
                .location(properties.requireLocation())
                .vertexAI(true)
                .httpOptions(clientHttpOptions())
                .build();
    }
}
