package com.terraformers.modernization.storage;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadObjectStorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd")
            .withZone(ZoneOffset.UTC);

    private final ObjectWriter objectWriter;
    private final String sourceBucket;
    private final String sourcePrefix;

    public UploadObjectStorageService(
            ObjectWriter objectWriter,
            @Value("${terraformers.upload.source-bucket:example-bucket}") String sourceBucket,
            @Value("${terraformers.upload.source-prefix:browser-uploads}") String sourcePrefix
    ) {
        this.objectWriter = objectWriter;
        this.sourceBucket = normalizeBucket(sourceBucket);
        this.sourcePrefix = normalizePrefix(sourcePrefix);
    }

    public StoredUploadObject store(MultipartFile file, String projectId, String originalFilename) {
        String sourceKey = buildSourceKey(projectId, originalFilename);
        try {
            ObjectWriteResult result = objectWriter.writeBytes(new ObjectBinaryWriteRequest(
                    sourceBucket, sourceKey, file.getBytes(), resolveContentType(file)));
            return StoredUploadObject.from(result);
        } catch (IOException | RuntimeException exception) {
            throw new UploadStorageException("failed to persist upload object: " + sourceKey, exception);
        }
    }

    private String buildSourceKey(String projectId, String originalFilename) {
        String datePath = DATE_PATH.format(Instant.now());
        return sourcePrefix + "/" + projectId + "/" + datePath + "/" + System.currentTimeMillis() + "-" + sanitizeFilename(originalFilename);
    }

    private String normalizeBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? "example-bucket" : bucket.strip();
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "browser-uploads";
        String normalized = prefix.strip();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.isBlank() ? "browser-uploads" : normalized;
    }

    private String sanitizeFilename(String filename) {
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "architecture-image.png" : sanitized;
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }
}
