package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.FinishReason;
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VertexArchitectureFactsExtractorTest {

    private static final String SECRET_PAYLOAD = "Bearer secret-token prompt-and-image-base64";

    @Test
    void usesLowThinkingWithinTheExistingFactTokenBound() {
        VertexArchitectureFactsExtractor extractor = extractor((modelId, content, config) -> {
            assertThat(config.maxOutputTokens()).contains(800);
            assertThat(config.thinkingConfig()).isPresent();
            assertThat(config.thinkingConfig().orElseThrow().thinkingLevel()).isPresent();
            assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().knownEnum())
                    .isEqualTo(ThinkingLevel.Known.LOW);
            assertThat(VertexArchitectureFactsExtractor.FACTS_PROMPT)
                    .contains("Terraform AWS provider resource type identifiers")
                    .contains("aws_[a-z0-9_]+", "aws_vpc", "aws_db_instance", "aws_security_group", "aws_lb")
                    .contains("AWS::EC2::VPC", "AWS::RDS::DBInstance")
                    .contains("empty", "resourceTypes")
                    .contains("components and relationships to at most 8")
                    .contains("resourceTypes to at most 16");
            return response("""
                    {"summary":"Three tier","components":["ALB","API"],
                     "relationships":["ALB -> API"],"resourceTypes":["aws_lb"]}
                    """);
        });

        extractor.extract(source());
    }

    @Test
    void boundsFactArraysWhileAllowingSixteenResourceTypes() {
        String components = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> "\"component-" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String relationships = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> "\"relationship-" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String resources = java.util.stream.IntStream.rangeClosed(1, 17)
                .mapToObj(index -> "\"aws_service_" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        VertexArchitectureFactsExtractor extractor = extractor((modelId, content, config) ->
                response("""
                        {"summary":"Large architecture","components":[%s],
                         "relationships":[%s],"resourceTypes":[%s]}
                        """.formatted(components, relationships, resources)));

        ArchitectureRetrievalFacts facts = extractor.extract(source());

        assertThat(facts.components()).hasSize(VertexArchitectureFactsExtractor.MAX_FACT_LIST_ITEMS);
        assertThat(facts.relationships()).hasSize(VertexArchitectureFactsExtractor.MAX_FACT_LIST_ITEMS);
        assertThat(facts.resourceTypes())
                .hasSize(ReferenceQuery.MAX_RESOURCE_TYPES)
                .contains("aws_service_16")
                .doesNotContain("aws_service_17");
    }

    @Test
    void extractsSuccessfulFactsWithoutChangingTheirValues() {
        VertexArchitectureFactsExtractor extractor = extractor((modelId, content, config) ->
                response("""
                        {"summary":" Three tier ","components":[" ALB ","API",""],
                         "relationships":["ALB -> API"],"resourceTypes":["aws_lb"]}
                        """));

        ArchitectureRetrievalFacts facts = extractor.extract(source());

        assertThat(facts.summary()).isEqualTo("Three tier");
        assertThat(facts.components()).containsExactly("ALB", "API");
        assertThat(facts.relationships()).containsExactly("ALB -> API");
        assertThat(facts.resourceTypes()).containsExactly("aws_lb");
    }

    @Test
    void sanitizesProviderCallFailure() {
        VertexArchitectureFactsExtractor extractor = extractor((modelId, content, config) -> {
            throw new IllegalStateException(SECRET_PAYLOAD);
        });

        ArchitectureFactsExtractionException failure = failure(extractor);

        assertThat(failure.reason()).isEqualTo(ArchitectureFactsExtractionException.Reason.PROVIDER_ERROR);
        assertThat(failure.evaluationDetail())
                .isEqualTo("reason=PROVIDER_ERROR;providerErrorType=IllegalStateException")
                .doesNotContain("secret-token", "prompt", "image", "base64");
    }

    @Test
    void distinguishesMaxTokens() {
        ArchitectureFactsExtractionException failure = failure(extractor((modelId, content, config) ->
                new VertexArchitectureFactsExtractor.VertexFactsResponse(
                        SECRET_PAYLOAD, FinishReason.Known.MAX_TOKENS)));

        assertThat(failure.reason()).isEqualTo(ArchitectureFactsExtractionException.Reason.RESPONSE_TRUNCATED);
        assertThat(failure.evaluationDetail()).isEqualTo("reason=RESPONSE_TRUNCATED");
    }

    @Test
    void distinguishesEmptyResponse() {
        ArchitectureFactsExtractionException failure = failure(extractor((modelId, content, config) ->
                response("  ")));

        assertThat(failure.reason()).isEqualTo(ArchitectureFactsExtractionException.Reason.EMPTY_RESPONSE);
    }

    @Test
    void distinguishesExplicitSafetyBlockWithoutPayloadEvidence() {
        ArchitectureFactsExtractionException failure = failure(extractor((modelId, content, config) ->
                new VertexArchitectureFactsExtractor.VertexFactsResponse(
                        SECRET_PAYLOAD, FinishReason.Known.SAFETY)));

        assertThat(failure.reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.PROVIDER_CONTENT_BLOCKED);
        assertThat(failure.evaluationDetail()).isEqualTo("reason=PROVIDER_CONTENT_BLOCKED")
                .doesNotContain(SECRET_PAYLOAD);
    }

    @Test
    void distinguishesRateLimitTimeoutAndAbnormalFinishWithoutPayloadLeakage() {
        com.google.genai.errors.ClientException rateLimited =
                org.mockito.Mockito.mock(com.google.genai.errors.ClientException.class);
        org.mockito.Mockito.when(rateLimited.code()).thenReturn(429);
        ArchitectureFactsExtractionException throttled = failure(extractor((modelId, content, config) -> {
            throw rateLimited;
        }));
        assertThat(throttled.reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.PROVIDER_RATE_LIMITED);

        ArchitectureFactsExtractionException timeout = failure(extractor((modelId, content, config) -> {
            throw new RuntimeException(new HttpTimeoutException("SENTINEL_PROVIDER_PAYLOAD"));
        }));
        assertThat(timeout.reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.PROVIDER_TIMEOUT);
        assertThat(timeout.evaluationDetail()).doesNotContain("SENTINEL_PROVIDER_PAYLOAD");

        ArchitectureFactsExtractionException abnormal = failure(extractor((modelId, content, config) ->
                new VertexArchitectureFactsExtractor.VertexFactsResponse(
                        "{\"summary\":\"looks valid\",\"components\":[],\"relationships\":[],\"resourceTypes\":[]}",
                        FinishReason.Known.OTHER)));
        assertThat(abnormal.reason()).isEqualTo(
                ArchitectureFactsExtractionException.Reason.INVALID_RESPONSE);
    }

    @Test
    void distinguishesMalformedJsonAndInvalidFieldShape() {
        ArchitectureFactsExtractionException malformed = failure(extractor((modelId, content, config) ->
                response("{not-json:")));
        ArchitectureFactsExtractionException invalidShape = failure(extractor((modelId, content, config) ->
                response("""
                        {"summary":[],"components":[],"relationships":[],"resourceTypes":[]}
                        """)));

        assertThat(malformed.reason()).isEqualTo(ArchitectureFactsExtractionException.Reason.INVALID_RESPONSE);
        assertThat(invalidShape.reason()).isEqualTo(ArchitectureFactsExtractionException.Reason.INVALID_RESPONSE);
        assertThat(malformed.evaluationDetail()).doesNotContain("not-json");
    }

    @Test
    void distinguishesParsedEmptyFacts() {
        ArchitectureFactsExtractionException failure = failure(extractor((modelId, content, config) ->
                response("""
                        {"summary":"","components":[],"relationships":[],"resourceTypes":[]}
                        """)));

        assertThat(failure.reason()).isEqualTo(ArchitectureFactsExtractionException.Reason.EMPTY_FACTS);
    }

    private VertexArchitectureFactsExtractor extractor(VertexArchitectureFactsExtractor.VertexFactsClient client) {
        return new VertexArchitectureFactsExtractor(client, new ObjectMapper(), new VertexRuntimeProperties());
    }

    private VertexArchitectureFactsExtractor.VertexFactsResponse response(String text) {
        return new VertexArchitectureFactsExtractor.VertexFactsResponse(
                text, FinishReason.Known.FINISH_REASON_UNSPECIFIED);
    }

    private ArchitectureFactsExtractionException failure(VertexArchitectureFactsExtractor extractor) {
        return catchThrowableOfType(
                () -> extractor.extract(source()),
                ArchitectureFactsExtractionException.class
        );
    }

    private ObjectContent source() {
        byte[] bytes = "safe-image-fixture".getBytes(StandardCharsets.UTF_8);
        return new ObjectContent(
                new ObjectMetadata("evaluation", "case", "image/png", bytes.length, "fixture-etag"),
                bytes
        );
    }
}
