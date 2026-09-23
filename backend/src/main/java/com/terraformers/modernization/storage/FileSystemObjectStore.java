package com.terraformers.modernization.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** A deterministic, single-runtime object store fixture backed only by JDK file APIs. */
@Component
@ConditionalOnProperty(
        prefix = "terraformers.storage",
        name = {"reader-provider", "writer-provider"},
        havingValue = "filesystem")
public class FileSystemObjectStore implements ObjectReader, ObjectWriter {

    private static final String PROVIDER = "filesystem";
    private static final String METADATA_SUFFIX = ".terraformers-meta";
    private final Path root;

    public FileSystemObjectStore(
            @Value("${terraformers.storage.filesystem.root-path:/tmp/terraformers-object-store}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public ObjectWriteResult writeText(ObjectWriteRequest request) {
        return write(request.bucket(), request.key(), request.content().getBytes(StandardCharsets.UTF_8), request.contentType());
    }

    @Override
    public ObjectWriteResult writeBytes(ObjectBinaryWriteRequest request) {
        return write(request.bucket(), request.key(), request.bytes(), request.contentType());
    }

    @Override
    public ObjectMetadata readMetadata(ObjectReference reference) {
        Path objectPath = resolve(reference.bucket(), reference.key());
        if (!Files.isRegularFile(objectPath)) {
            throw failure(ObjectStorageException.Reason.NOT_FOUND, "Object was not found: " + reference.bucket() + "/" + reference.key(), null);
        }
        try {
            byte[] bytes = Files.readAllBytes(objectPath);
            return new ObjectMetadata(reference.bucket(), reference.key(), readContentType(objectPath), bytes.length,
                    sha256(bytes), Files.getLastModifiedTime(objectPath).toInstant());
        } catch (IOException e) {
            throw failure(ObjectStorageException.Reason.UPSTREAM_FAILURE, "Could not read object metadata", e);
        }
    }

    @Override
    public ObjectContent readContent(ObjectReference reference) {
        Path objectPath = resolve(reference.bucket(), reference.key());
        ObjectMetadata metadata = readMetadata(reference);
        try {
            return new ObjectContent(metadata, Files.readAllBytes(objectPath));
        } catch (IOException e) {
            throw failure(ObjectStorageException.Reason.UPSTREAM_FAILURE, "Could not read object content", e);
        }
    }

    private ObjectWriteResult write(String bucket, String key, byte[] bytes, String contentType) {
        Path objectPath = resolve(bucket, key);
        try {
            Files.createDirectories(objectPath.getParent());
            Files.write(objectPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(metadataPath(objectPath), contentType == null ? "" : contentType, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return new ObjectWriteResult(PROVIDER, true, bucket, key, sha256(bytes));
        } catch (IOException e) {
            throw failure(ObjectStorageException.Reason.UPSTREAM_FAILURE, "Could not write object", e);
        }
    }

    private Path resolve(String bucket, String key) {
        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
            throw failure(ObjectStorageException.Reason.UPSTREAM_FAILURE, "Object bucket and key must not be blank", null);
        }
        Path bucketRoot = root.resolve(bucket).normalize();
        Path objectPath = bucketRoot.resolve(key).normalize();
        if (!bucketRoot.startsWith(root) || !objectPath.startsWith(bucketRoot)) {
            throw failure(ObjectStorageException.Reason.UPSTREAM_FAILURE, "Object path escapes the configured filesystem root", null);
        }
        return objectPath;
    }

    private String readContentType(Path objectPath) throws IOException {
        Path metadataPath = metadataPath(objectPath);
        return Files.isRegularFile(metadataPath)
                ? Files.readString(metadataPath, StandardCharsets.UTF_8)
                : "application/octet-stream";
    }

    private Path metadataPath(Path objectPath) {
        return objectPath.resolveSibling(objectPath.getFileName() + METADATA_SUFFIX);
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", e);
        }
    }

    private ObjectStorageException failure(ObjectStorageException.Reason reason, String message, Throwable cause) {
        return new ObjectStorageException(reason, message, cause);
    }
}
