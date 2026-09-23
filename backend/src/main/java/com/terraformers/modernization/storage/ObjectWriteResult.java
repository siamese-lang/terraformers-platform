package com.terraformers.modernization.storage;

public record ObjectWriteResult(
        String provider,
        boolean persisted,
        String bucket,
        String key,
        String eTag
) {
    public ObjectReference reference() {
        return new ObjectReference(bucket, key);
    }
}
