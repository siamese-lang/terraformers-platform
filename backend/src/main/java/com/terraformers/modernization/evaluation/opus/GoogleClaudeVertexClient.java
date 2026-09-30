package com.terraformers.modernization.evaluation.opus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class GoogleClaudeVertexClient implements ClaudeVertexClient {
    public static final String PUBLISHER = "anthropic";
    public static final String MODEL = "claude-opus-5-5";
    public static final String LOCATION = "global";
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private final ObjectMapper mapper;
    private final GoogleCredentials credentials;
    private final HttpClient http;
    private final URI endpoint;

    public GoogleClaudeVertexClient(String projectId, ObjectMapper mapper) {
        this(projectId, mapper, applicationDefault(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
    }

    GoogleClaudeVertexClient(String projectId, ObjectMapper mapper, GoogleCredentials credentials, HttpClient http) {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("GOOGLE_CLOUD_PROJECT is required");
        this.mapper = mapper;
        this.credentials = credentials.createScoped(List.of(SCOPE));
        this.http = http;
        this.endpoint = URI.create("https://aiplatform.googleapis.com/v1/projects/" + projectId
                + "/locations/global/publishers/anthropic/models/claude-opus-5-5:rawPredict");
    }

    public URI endpoint() { return endpoint; }

    @Override public ClaudeResponse rawPredict(JsonNode request) {
        try {
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(3))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ClaudeProviderException(response.statusCode(), "MODEL_ACCESS",
                        "Claude Vertex rawPredict returned HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            StringBuilder text = new StringBuilder();
            for (JsonNode item : root.path("content")) if (item.path("text").isTextual()) text.append(item.path("text").asText());
            if (text.isEmpty()) throw new ClaudeProviderException(response.statusCode(), "PROVIDER_RUNTIME", "Claude response contained no text");
            return new ClaudeResponse(text.toString(), root.path("stop_reason").asText(),
                    integer(root.path("usage").path("input_tokens")), integer(root.path("usage").path("output_tokens")),
                    root.path("model").asText());
        } catch (ClaudeProviderException exception) { throw exception;
        } catch (IOException exception) { throw new ClaudeProviderException(null, "MODEL_ACCESS", exception.getClass().getSimpleName());
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ClaudeProviderException(null, "PROVIDER_RUNTIME", "InterruptedException"); }
    }

    private static Integer integer(JsonNode node) { return node.isIntegralNumber() ? node.intValue() : null; }
    private static GoogleCredentials applicationDefault() {
        try { return GoogleCredentials.getApplicationDefault(); }
        catch (IOException e) { throw new ClaudeProviderException(null, "MODEL_ACCESS", "ApplicationDefaultCredentialsUnavailable"); }
    }

    public static final class ClaudeProviderException extends RuntimeException {
        private final Integer httpStatus; private final String category;
        public ClaudeProviderException(Integer status, String category, String safeMessage) { super(safeMessage); this.httpStatus=status; this.category=category; }
        public Integer httpStatus() { return httpStatus; } public String category() { return category; }
    }
}
