package com.terraformers.modernization.storage;

import java.time.Instant;

public record ObjectMetadata(
        String bucket,
        String key,
        String contentType,
        long contentLength,
        String eTag,
        Instant lastModified
) {
    public ObjectMetadata(String bucket, String key, String contentType, long contentLength, String eTag) {
        this(bucket, key, contentType, contentLength, eTag, null);
    }
}
