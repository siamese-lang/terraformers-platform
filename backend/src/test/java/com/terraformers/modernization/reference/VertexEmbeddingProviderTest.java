package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VertexEmbeddingProviderTest {

    @Test
    void successfulAndClosureQueryEmbeddingsUseTenSecondBudgetForBothModelFamilies() {
        for (String model : java.util.List.of("gemini-embedding-2", "gemini-embedding-001")) {
            var p = new com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties();
            p.setEmbeddingModelId(model); p.setEmbeddingDimension(2);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var provider = new VertexEmbeddingProvider((id, text, config) -> {
                calls.incrementAndGet();
                assertThat(config.httpOptions().orElseThrow().timeout()).contains(10000);
                assertThat(config.outputDimensionality()).contains(2);
                return com.google.genai.types.EmbedContentResponse.builder().embeddings(java.util.List.of(
                        com.google.genai.types.ContentEmbedding.builder().values(java.util.List.of(0.1f, 0.2f)).build()))
                        .build();
            }, p);
            assertThat(provider.embed("initial query")).containsExactly(0.1f, 0.2f);
            assertThat(provider.embed("closure query")).containsExactly(0.1f, 0.2f);
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void embeddingTimeoutIsTranslatedOnceAndOrdinaryInterruptionIsNot() {
        var p = new com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var timeout = new VertexEmbeddingProvider((model, text, config) -> {
            calls.incrementAndGet();
            throw new com.google.genai.errors.GenAiIOException(new java.io.InterruptedIOException("timeout"));
        }, p);
        assertThatThrownBy(() -> timeout.embed("query"))
                .isInstanceOf(com.terraformers.modernization.analysis.AnalysisProviderTimeoutException.class);
        assertThat(calls).hasValue(1);
        var interrupted = new VertexEmbeddingProvider((model, text, config) -> {
            throw new com.google.genai.errors.GenAiIOException(new java.io.InterruptedIOException("interrupted"));
        }, p);
        assertThatThrownBy(() -> interrupted.embed("query"))
                .isInstanceOf(com.google.genai.errors.GenAiIOException.class);
    }

    @Test
    void embedding2UsesInlineSearchInstruction() {
        assertThat(VertexEmbeddingProvider.usesInlineSearchInstruction("gemini-embedding-2")).isTrue();
        assertThat(VertexEmbeddingProvider.prepareQueryInput(
                "gemini-embedding-2", "  architecture summary  "))
                .isEqualTo("task: search result | query: architecture summary");
    }

    @Test
    void legacyEmbeddingModelPreservesRawQueryText() {
        assertThat(VertexEmbeddingProvider.usesInlineSearchInstruction("gemini-embedding-001")).isFalse();
        assertThat(VertexEmbeddingProvider.prepareQueryInput(
                "gemini-embedding-001", "  architecture summary  "))
                .isEqualTo("architecture summary");
    }
}
