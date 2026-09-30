package com.terraformers.modernization.evaluation.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.types.GenerateContentResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;

/** Evaluation-only phase and transport telemetry for the FACT_REUSE diagnostic artifact. */
final class GeminiLatencyTelemetry {

    enum Arm { CONTROL, CANDIDATE }
    enum Phase { FACT_EXTRACTION, CANONICAL_EXTRACTION, GENERATION }

    private final Clock clock;
    private final ObjectMapper mapper;
    private final ThreadLocal<ActivePhase> active = new ThreadLocal<>();
    private final List<PhaseEvidence> completed = new ArrayList<>();
    private final AtomicInteger requestOrdinal = new AtomicInteger();

    GeminiLatencyTelemetry(ObjectMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    GeminiLatencyTelemetry(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    OkHttpClient instrumentedHttpClient() {
        return new OkHttpClient.Builder()
                // Match google-genai 1.72.0's default client: telemetry must not impose a timeout.
                .connectTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .eventListenerFactory(call -> new TransportEvents(this))
                .addInterceptor(new ResponseMetadataInterceptor(this))
                .build();
    }

    PhaseScope start(
            String caseId,
            Arm arm,
            Phase phase,
            RequestIdentity identity,
            PayloadShape payload
    ) {
        if (active.get() != null) throw new IllegalStateException("nested Gemini telemetry phase");
        ActivePhase value = new ActivePhase(caseId, arm, phase, identity, payload, now(), System.nanoTime());
        active.set(value);
        System.out.printf("FACT_REUSE case=%s arm=%s phase=%s START%n", caseId, arm, phase);
        return new PhaseScope(this, value);
    }

    List<PhaseEvidence> evidenceFor(String caseId, Arm arm) {
        synchronized (completed) {
            return completed.stream().filter(v -> v.caseId().equals(caseId) && v.arm() == arm).toList();
        }
    }

    void captureSdkResponse(GenerateContentResponse response) {
        ActiveRequest request = currentRequest();
        if (request == null || response == null) return;
        response.usageMetadata().ifPresent(metadata -> request.usage = new UsageEvidence(
                metadata.promptTokenCount().orElse(null),
                metadata.candidatesTokenCount().orElse(null),
                metadata.thoughtsTokenCount().orElse(null),
                metadata.totalTokenCount().orElse(null),
                metadata.promptTokensDetails().map(this::safeTree).orElse(null),
                metadata.candidatesTokensDetails().map(this::safeTree).orElse(null),
                metadata.trafficType().map(Object::toString).orElse(null)
        ));
        request.finishReason = response.finishReason() == null ? null : response.finishReason().toString();
    }

    private ActiveRequest currentRequest() {
        ActivePhase phase = active.get();
        return phase == null ? null : phase.currentRequest;
    }

    private void callStart() {
        ActivePhase phase = active.get();
        if (phase == null) return;
        ActiveRequest request = new ActiveRequest(requestOrdinal.incrementAndGet(), now(), System.nanoTime());
        phase.currentRequest = request;
        phase.requests.add(request);
    }

    private void requestHeadersStart() {
        ActiveRequest request = currentRequest();
        if (request == null) return;
        request.attempts.add(new ActiveAttempt(request.attempts.size() + 1, now()));
    }

    private void requestBodyEnd() {
        ActiveAttempt attempt = currentAttempt();
        if (attempt != null) attempt.requestBodyCompletedAt = now();
    }

    private void responseHeadersEnd(int status) {
        ActiveAttempt attempt = currentAttempt();
        if (attempt != null) {
            attempt.responseHeadersReceivedAt = now();
            attempt.httpStatus = status;
        }
    }

    private void responseBodyEnd() {
        ActiveAttempt attempt = currentAttempt();
        if (attempt != null) attempt.responseBodyCompletedAt = now();
    }

    private void callEnd() {
        ActiveRequest request = currentRequest();
        if (request != null) request.endedAt = now();
    }

    private void callFailed(IOException failure) {
        ActiveRequest request = currentRequest();
        if (request != null) {
            request.endedAt = now();
            request.errorType = safeType(failure);
            request.timeout = failure instanceof java.net.SocketTimeoutException;
        }
    }

    private ActiveAttempt currentAttempt() {
        ActiveRequest request = currentRequest();
        return request == null || request.attempts.isEmpty()
                ? null : request.attempts.get(request.attempts.size() - 1);
    }

    private void captureResponseMetadata(Response response) {
        ActiveRequest request = currentRequest();
        if (request == null) return;
        request.httpStatus = response.code();
        try {
            JsonNode root = mapper.readTree(response.peekBody(1024 * 1024).string());
            JsonNode usage = root.path("usageMetadata");
            if (usage.isObject()) {
                request.usage = new UsageEvidence(
                        nullableInt(usage, "promptTokenCount"),
                        nullableInt(usage, "candidatesTokenCount"),
                        nullableInt(usage, "thoughtsTokenCount"),
                        nullableInt(usage, "totalTokenCount"),
                        nullableNode(usage, "promptTokensDetails"),
                        nullableNode(usage, "candidatesTokensDetails"),
                        nullableText(usage, "trafficType")
                );
            }
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                request.finishReason = nullableText(candidates.get(0), "finishReason");
            }
        } catch (IOException ignored) {
            // Response content is never retained; missing/unparseable metadata remains null.
        }
    }

    private void captureRequestShape(Request request) {
        ActivePhase phase = active.get();
        if (phase == null || request.body() == null) return;
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            JsonNode root = mapper.readTree(buffer.readUtf8());
            ShapeCounts counts = new ShapeCounts();
            countPayload(root, counts);
            PayloadShape prior = phase.payload;
            phase.payload = new PayloadShape(
                    counts.imageBytes > 0 ? "IMAGE_AND_TEXT" : "TEXT",
                    counts.imageBytes,
                    counts.textChars,
                    counts.textBytes,
                    prior.referenceCount(),
                    prior.referenceTextCharCount(),
                    prior.referenceTextByteCount());
        } catch (RuntimeException | IOException ignored) {
            // Keep caller-supplied safe shape evidence if the encoded request cannot be inspected.
        }
    }

    private void countPayload(JsonNode node, ShapeCounts counts) {
        if (node == null) return;
        if (node.isObject()) {
            if (node.has("text") && node.get("text").isTextual()) {
                String value = node.get("text").textValue();
                counts.textChars += value.length();
                counts.textBytes += value.getBytes(StandardCharsets.UTF_8).length;
            }
            JsonNode data = node.path("inlineData").path("data");
            if (data.isTextual()) {
                try { counts.imageBytes += Base64.getDecoder().decode(data.textValue()).length; }
                catch (IllegalArgumentException ignored) { /* malformed data is not telemetry evidence */ }
            }
            node.elements().forEachRemaining(child -> countPayload(child, counts));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> countPayload(child, counts));
        }
    }

    private void complete(ActivePhase phase, Throwable failure) {
        if (active.get() != phase) throw new IllegalStateException("Gemini telemetry phase context changed");
        active.remove();
        Instant endedAt = now();
        long elapsedMs = (System.nanoTime() - phase.startedNanos) / 1_000_000;
        List<RequestEvidence> requests = phase.requests.stream().map(this::snapshot).toList();
        PhaseEvidence evidence = new PhaseEvidence(
                phase.caseId, phase.arm, phase.phase, phase.startedAt, endedAt, elapsedMs,
                phase.identity, phase.payload, failure == null, failure == null ? null : safeType(failure), requests);
        synchronized (completed) { completed.add(evidence); }
        System.out.printf("FACT_REUSE case=%s arm=%s phase=%s END elapsedMs=%d success=%s%n",
                phase.caseId, phase.arm, phase.phase, elapsedMs, failure == null);
    }

    private RequestEvidence snapshot(ActiveRequest request) {
        Instant ended = request.endedAt == null ? now() : request.endedAt;
        return new RequestEvidence(request.ordinal, request.startedAt, ended,
                Math.max(0, java.time.Duration.between(request.startedAt, ended).toMillis()),
                request.attempts.isEmpty() ? null : request.attempts.size(),
                request.attempts.stream().map(ActiveAttempt::snapshot).toList(),
                request.httpStatus, request.timeout, request.errorType, request.finishReason, request.usage);
    }

    private Instant now() { return clock.instant(); }
    private JsonNode safeTree(Object value) {
        try { return mapper.valueToTree(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private static String safeType(Throwable failure) {
        String name = failure.getClass().getSimpleName();
        return name.matches("[A-Za-z0-9_.-]{1,80}") ? name : "RuntimeException";
    }
    private static Integer nullableInt(JsonNode node, String field) {
        return node.has(field) && node.get(field).canConvertToInt() ? node.get(field).intValue() : null;
    }
    private static String nullableText(JsonNode node, String field) {
        return node.has(field) && node.get(field).isTextual() ? node.get(field).textValue() : null;
    }
    private static JsonNode nullableNode(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).deepCopy() : null;
    }

    final class PhaseScope {
        private final GeminiLatencyTelemetry owner;
        private final ActivePhase phase;
        private boolean closed;
        private PhaseScope(GeminiLatencyTelemetry owner, ActivePhase phase) { this.owner = owner; this.phase = phase; }
        void close(Throwable failure) {
            if (!closed) { closed = true; owner.complete(phase, failure); }
        }
    }

    record RequestIdentity(String modelId, String location, String configuredThinkingLevel, int maxOutputTokens) {}
    record PayloadShape(String inputModality, int imageByteCount, int promptTextCharCount,
                        int promptTextByteCount, int referenceCount, int referenceTextCharCount,
                        int referenceTextByteCount) {
        static PayloadShape of(String modality, int imageBytes, String prompt, List<String> references) {
            String safePrompt = prompt == null ? "" : prompt;
            List<String> safeReferences = references == null ? List.of() : references;
            String joined = String.join("\n", safeReferences);
            return new PayloadShape(modality, imageBytes, safePrompt.length(),
                    safePrompt.getBytes(StandardCharsets.UTF_8).length, safeReferences.size(), joined.length(),
                    joined.getBytes(StandardCharsets.UTF_8).length);
        }
    }
    record UsageEvidence(Integer promptTokenCount, Integer candidatesTokenCount, Integer thoughtsTokenCount,
                         Integer totalTokenCount, Object promptTokensDetails, Object candidatesTokensDetails,
                         String trafficType) {}
    record AttemptEvidence(int physicalAttemptOrdinal, Instant requestStartedAt, Instant requestBodyCompletedAt,
                           Instant responseHeadersReceivedAt, Instant responseBodyCompletedAt, Integer httpStatus) {}
    record RequestEvidence(int logicalRequestOrdinal, Instant requestStartedAt, Instant responseCompletedAt,
                           long elapsedMs, Integer physicalAttemptCount, List<AttemptEvidence> physicalAttempts,
                           Integer httpStatus, Boolean timeout, String errorType, String finishReason,
                           UsageEvidence usage) {}
    record PhaseEvidence(String caseId, Arm arm, Phase phase, Instant startedAt, Instant endedAt, long elapsedMs,
                         RequestIdentity requestIdentity, PayloadShape payload, boolean succeeded,
                         String errorType, List<RequestEvidence> requests) {}

    private static final class ActivePhase {
        final String caseId; final Arm arm; final Phase phase; final RequestIdentity identity;
        PayloadShape payload; final Instant startedAt; final long startedNanos;
        final List<ActiveRequest> requests = new ArrayList<>(); ActiveRequest currentRequest;
        ActivePhase(String c, Arm a, Phase p, RequestIdentity i, PayloadShape s, Instant at, long nanos) {
            caseId = c; arm = a; phase = p; identity = i; payload = s; startedAt = at; startedNanos = nanos;
        }
    }
    private static final class ShapeCounts { int imageBytes; int textChars; int textBytes; }
    private static final class ActiveRequest {
        final int ordinal; final Instant startedAt; final long startedNanos; final List<ActiveAttempt> attempts = new ArrayList<>();
        Instant endedAt; Integer httpStatus; Boolean timeout; String errorType; String finishReason; UsageEvidence usage;
        ActiveRequest(int ordinal, Instant startedAt, long startedNanos) { this.ordinal = ordinal; this.startedAt = startedAt; this.startedNanos = startedNanos; }
    }
    private static final class ActiveAttempt {
        final int ordinal; final Instant requestStartedAt; Instant requestBodyCompletedAt;
        Instant responseHeadersReceivedAt; Instant responseBodyCompletedAt; Integer httpStatus;
        ActiveAttempt(int ordinal, Instant startedAt) { this.ordinal = ordinal; this.requestStartedAt = startedAt; }
        AttemptEvidence snapshot() { return new AttemptEvidence(ordinal, requestStartedAt, requestBodyCompletedAt,
                responseHeadersReceivedAt, responseBodyCompletedAt, httpStatus); }
    }

    private static final class TransportEvents extends EventListener {
        private final GeminiLatencyTelemetry telemetry;
        TransportEvents(GeminiLatencyTelemetry telemetry) { this.telemetry = telemetry; }
        @Override public void callStart(Call call) { telemetry.callStart(); }
        @Override public void requestHeadersStart(Call call) { telemetry.requestHeadersStart(); }
        @Override public void requestBodyEnd(Call call, long byteCount) { telemetry.requestBodyEnd(); }
        @Override public void responseHeadersEnd(Call call, Response response) { telemetry.responseHeadersEnd(response.code()); }
        @Override public void responseBodyEnd(Call call, long byteCount) { telemetry.responseBodyEnd(); }
        @Override public void callEnd(Call call) { telemetry.callEnd(); }
        @Override public void callFailed(Call call, IOException ioe) { telemetry.callFailed(ioe); }
    }
    private static final class ResponseMetadataInterceptor implements Interceptor {
        private final GeminiLatencyTelemetry telemetry;
        ResponseMetadataInterceptor(GeminiLatencyTelemetry telemetry) { this.telemetry = telemetry; }
        @Override public Response intercept(Chain chain) throws IOException {
            telemetry.captureRequestShape(chain.request());
            Response response = chain.proceed(chain.request());
            telemetry.captureResponseMetadata(response);
            return response;
        }
    }
}
