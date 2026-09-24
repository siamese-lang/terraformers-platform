package com.terraformers.modernization.reference.opensearch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class HttpOpenSearchTransport implements OpenSearchTransport {

    private final HttpClient httpClient;

    public HttpOpenSearchTransport() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    HttpOpenSearchTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String post(URI uri, String body) {
        requireHttpUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "OpenSearch query failed with HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("OpenSearch query I/O failure", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenSearch query interrupted", exception);
        }
    }

    private void requireHttpUri(URI uri) {
        if (uri == null || uri.getScheme() == null
                || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("OpenSearch endpoint must use http or https");
        }
    }
}
