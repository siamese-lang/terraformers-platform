package com.terraformers.modernization.reference.opensearch;

import java.net.URI;

public interface OpenSearchTransport {
    String post(URI uri, String body);
}
