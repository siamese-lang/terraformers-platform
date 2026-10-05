package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VertexEmbeddingProviderTest {

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
