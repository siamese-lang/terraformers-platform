package com.terraformers.modernization.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "terraformers.storage", name = "reader-provider", havingValue = "gcs")
public class GcsObjectReader implements ObjectReader {

    private final Storage storage;

    public GcsObjectReader() {
        this(StorageOptions.getDefaultInstance().getService());
    }

    GcsObjectReader(Storage storage) {
        this.storage = storage;
    }

    @Override
    public ObjectMetadata readMetadata(ObjectReference reference) {
        try {
            return metadata(reference, requireBlob(reference));
        } catch (StorageException exception) {
            throw translate(reference, exception);
        }
    }

    @Override
    public ObjectContent readContent(ObjectReference reference) {
        try {
            Blob blob = requireBlob(reference);
            return new ObjectContent(metadata(reference, blob), blob.getContent());
        } catch (StorageException exception) {
            throw translate(reference, exception);
        }
    }

    private Blob requireBlob(ObjectReference reference) {
        Blob blob = storage.get(BlobId.of(reference.bucket(), reference.key()));
        if (blob == null) {
            throw new ObjectStorageException(
                    ObjectStorageException.Reason.NOT_FOUND,
                    "object not found: " + reference.key(),
                    null
            );
        }
        return blob;
    }

    private ObjectMetadata metadata(ObjectReference reference, Blob blob) {
        Long size = blob.getSize();
        OffsetDateTime updateTime = blob.getUpdateTimeOffsetDateTime();
        Instant lastModified = updateTime == null ? null : updateTime.toInstant();
        return new ObjectMetadata(
                reference.bucket(),
                reference.key(),
                blob.getContentType(),
                size == null ? 0L : size,
                blob.getEtag(),
                lastModified
        );
    }

    private ObjectStorageException translate(ObjectReference reference, StorageException exception) {
        ObjectStorageException.Reason reason = exception.getCode() == 404
                ? ObjectStorageException.Reason.NOT_FOUND
                : ObjectStorageException.Reason.UPSTREAM_FAILURE;
        return new ObjectStorageException(reason, "failed to read object: " + reference.key(), exception);
    }
}
