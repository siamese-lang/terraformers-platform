package com.terraformers.modernization.reference.opensearch;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SelectedOpenSearchTransport implements OpenSearchTransport {

    private final AnalysisRuntimeProperties properties;
    private final ObjectProvider<HttpOpenSearchTransport> http;
    private final ObjectProvider<SignedOpenSearchHttpClient> awsSigV4;

    public SelectedOpenSearchTransport(
            AnalysisRuntimeProperties properties,
            ObjectProvider<HttpOpenSearchTransport> http,
            ObjectProvider<SignedOpenSearchHttpClient> awsSigV4
    ) {
        this.properties = properties;
        this.http = http;
        this.awsSigV4 = awsSigV4;
    }

    @Override
    public String post(URI uri, String body) {
        return switch (properties.resolvedOpenSearchTransport()) {
            case HTTP -> http.getObject().post(uri, body);
            case AWS_SIGV4 -> awsSigV4.getObject().post(uri, body);
        };
    }
}
