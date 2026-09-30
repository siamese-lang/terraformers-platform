package com.terraformers.modernization.evaluation.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

class GeminiLatencyTelemetryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void clonedClientPreservesListenerAndCapturesObservedExchangesTimingPayloadAndUsage() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/generate", exchange -> {
            int call = calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] body = (call == 1 ? "{}" : """
                    {"candidates":[{"finishReason":"STOP"}],"usageMetadata":{
                      "promptTokenCount":12,"candidatesTokenCount":7,"thoughtsTokenCount":3,
                      "totalTokenCount":22,"promptTokensDetails":[{"modality":"TEXT","tokenCount":8}],
                      "candidatesTokensDetails":[{"modality":"TEXT","tokenCount":7}],
                      "trafficType":"ON_DEMAND"}}
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(call == 1 ? 503 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(mapper);
            OkHttpClient base = telemetry.instrumentedHttpClient();
            OkHttpClient cloned = base.newBuilder().addInterceptor(chain -> {
                Response first = chain.proceed(chain.request());
                if (first.code() != 503) return first;
                first.close();
                return chain.proceed(chain.request());
            }).build();
            assertThat(cloned.connectTimeoutMillis()).isZero();
            assertThat(cloned.readTimeoutMillis()).isZero();
            assertThat(cloned.writeTimeoutMillis()).isZero();
            assertThat(cloned.callTimeoutMillis()).isZero();

            var scope = telemetry.start("case", GeminiLatencyTelemetry.Arm.CONTROL,
                    GeminiLatencyTelemetry.Phase.FACT_EXTRACTION,
                    identity(), GeminiLatencyTelemetry.PayloadShape.of("IMAGE_AND_TEXT", 3, "", List.of()));
            Throwable failure = null;
            try (Response ignored = cloned.newCall(request(server.getAddress().getPort())).execute()) {
                // response is consumed by close; telemetry observes the final response metadata without retaining it
            } catch (Throwable exception) {
                failure = exception;
                throw exception;
            } finally {
                scope.close(failure);
            }

            var phase = telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CONTROL).get(0);
            assertThat(phase.requests()).hasSize(1);
            var request = phase.requests().get(0);
            assertThat(request.observedHttpExchangeCount()).isEqualTo(2);
            assertThat(request.observedHttpExchanges())
                    .extracting(GeminiLatencyTelemetry.HttpExchangeEvidence::httpStatus)
                    .containsExactly(503, 200);
            assertThat(request.usage().promptTokenCount()).isEqualTo(12);
            assertThat(request.usage().thoughtsTokenCount()).isEqualTo(3);
            assertThat(request.usage().totalTokenCount()).isEqualTo(22);
            assertThat(request.finishReason()).isEqualTo("STOP");
            assertThat(phase.payload().requestTextCharCount()).isPositive();
            assertThat(phase.payload().imageByteCount()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void absentUsageStaysNull() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/generate", exchange -> {
            byte[] body = "{\"candidates\":[{\"finishReason\":\"STOP\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(mapper);
            var scope = telemetry.start("case", GeminiLatencyTelemetry.Arm.CANDIDATE,
                    GeminiLatencyTelemetry.Phase.GENERATION, identity(),
                    GeminiLatencyTelemetry.PayloadShape.of("TEXT", 0, "", List.of()));
            try (Response ignored = telemetry.instrumentedHttpClient().newCall(request(server.getAddress().getPort())).execute()) {
                // no-op
            } finally {
                scope.close(null);
            }
            assertThat(telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CANDIDATE)
                    .get(0).requests().get(0).usage()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preHeaderConnectFailureIsNetworkEvidenceNotAnHttpExchangeOrExactAttemptCount() {
        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(mapper);
        var scope = telemetry.start("case", GeminiLatencyTelemetry.Arm.CONTROL,
                GeminiLatencyTelemetry.Phase.FACT_EXTRACTION, identity(),
                GeminiLatencyTelemetry.PayloadShape.of("IMAGE_AND_TEXT", 3, "", List.of()));
        telemetry.callStart();
        telemetry.connectFailed(new java.net.ConnectException("sensitive endpoint detail"));
        scope.close(new IllegalStateException("sensitive provider detail"));

        var request = telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CONTROL)
                .get(0).requests().get(0);
        assertThat(request.observedHttpExchangeCount()).isZero();
        assertThat(request.networkFailureCount()).isEqualTo(1);
        assertThat(request.networkFailures()).containsExactly(
                new GeminiLatencyTelemetry.NetworkFailureEvidence("CONNECT_FAILED", "ConnectException"));
        assertThat(request.toString()).doesNotContain("sensitive endpoint detail");
    }

    @Test
    void propagatedProviderFailurePopulatesLastRequestSafeErrorType() {
        GeminiLatencyTelemetry telemetry = new GeminiLatencyTelemetry(mapper);
        var scope = telemetry.start("case", GeminiLatencyTelemetry.Arm.CANDIDATE,
                GeminiLatencyTelemetry.Phase.GENERATION, identity(),
                GeminiLatencyTelemetry.PayloadShape.of("TEXT", 0, "", List.of()));
        telemetry.callStart();
        scope.close(new IllegalStateException("sensitive provider detail"));

        var request = telemetry.evidenceFor("case", GeminiLatencyTelemetry.Arm.CANDIDATE)
                .get(0).requests().get(0);
        assertThat(request.errorType()).isEqualTo("IllegalStateException");
        assertThat(request.toString()).doesNotContain("sensitive provider detail");
    }

    private Request request(int port) {
        String body = """
                {"contents":[{"parts":[{"inlineData":{"data":"YWJj"}},{"text":"safe prompt"}]}]}
                """;
        return new Request.Builder().url("http://127.0.0.1:" + port + "/generate")
                .post(RequestBody.create(body, MediaType.get("application/json"))).build();
    }

    private GeminiLatencyTelemetry.RequestIdentity identity() {
        return new GeminiLatencyTelemetry.RequestIdentity("gemini-3.8-flash", "global", "LOW", 800);
    }
}
