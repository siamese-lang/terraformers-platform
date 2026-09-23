package com.terraformers.modernization.storage;

public record ObjectBinaryWriteRequest(
        String bucket,
        String key,
        byte[] bytes,
        String contentType
) {
}
