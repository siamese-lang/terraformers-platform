package com.terraformers.modernization.reference.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class SelectedOpenSearchTransportTest {

    @Test
    void routesTargetRuntimeToInternalHttpTransport() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setOpensearchTransport("http");
        HttpOpenSearchTransport http = mock(HttpOpenSearchTransport.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<HttpOpenSearchTransport> httpProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SignedOpenSearchHttpClient> awsProvider = mock(ObjectProvider.class);
        URI uri = URI.create("http://terraformers-opensearch:9200/references/_search");
        when(httpProvider.getObject()).thenReturn(http);
        when(http.post(uri, "{}")).thenReturn("{\"hits\":{}}");

        String response = new SelectedOpenSearchTransport(properties, httpProvider, awsProvider)
                .post(uri, "{}");

        assertThat(response).contains("hits");
        verify(http).post(uri, "{}");
        verifyNoInteractions(awsProvider);
    }

    @Test
    void preservesExplicitAwsSigV4CompatibilitySelection() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setOpensearchTransport("aws-sigv4");
        @SuppressWarnings("unchecked")
        ObjectProvider<HttpOpenSearchTransport> httpProvider = mock(ObjectProvider.class);
        SignedOpenSearchHttpClient aws = mock(SignedOpenSearchHttpClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SignedOpenSearchHttpClient> awsProvider = mock(ObjectProvider.class);
        URI uri = URI.create("https://example.aoss.amazonaws.com/references/_search");
        when(awsProvider.getObject()).thenReturn(aws);
        when(aws.post(uri, "{}")).thenReturn("{\"hits\":{}}");

        String response = new SelectedOpenSearchTransport(properties, httpProvider, awsProvider)
                .post(uri, "{}");

        assertThat(response).contains("hits");
        verify(aws).post(uri, "{}");
        verifyNoInteractions(httpProvider);
    }
}
