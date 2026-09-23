package com.terraformers.modernization.storage;

public record StoredUploadObject(
        String provider,
        boolean binaryPersisted,
        String bucket,
        String key,
        String eTag
) {
    public static StoredUploadObject metadataOnly(String bucket, String key) {
        return new StoredUploadObject("metadata-only", false, bucket, key, null);
    }

    public static StoredUploadObject from(ObjectWriteResult result) {
        return new StoredUploadObject(result.provider(), result.persisted(), result.bucket(), result.key(), result.eTag());
    }
}
