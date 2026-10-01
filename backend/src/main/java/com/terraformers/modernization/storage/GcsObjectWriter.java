package com.terraformers.modernization.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "terraformers.storage", name = "writer-provider", havingValue = "gcs")
public class GcsObjectWriter implements ObjectWriter, ObjectRemover {

    private final Storage storage;

    public GcsObjectWriter() {
        this(StorageOptions.getDefaultInstance().getService());
    }

    GcsObjectWriter(Storage storage) {
        this.storage = storage;
    }

    @Override
    public ObjectWriteResult writeText(ObjectWriteRequest request) {
        return writeBytes(new ObjectBinaryWriteRequest(
                request.bucket(),
                request.key(),
                request.content().getBytes(StandardCharsets.UTF_8),
                request.contentType()
        ));
    }

    @Override
    public ObjectWriteResult writeBytes(ObjectBinaryWriteRequest request) {
        try {
            BlobInfo info = BlobInfo.newBuilder(BlobId.of(request.bucket(), request.key()))
                    .setContentType(request.contentType())
                    .build();
            Blob blob = storage.create(info, request.bytes());
            return new ObjectWriteResult("gcs", true, request.bucket(), request.key(), blob.getEtag());
        } catch (StorageException exception) {
            throw new ObjectStorageException(
                    ObjectStorageException.Reason.UPSTREAM_FAILURE,
                    "failed to write object: " + request.key(),
                    exception
            );
        }
    }

    @Override
    public void remove(ObjectReference reference) {
        try {
            storage.delete(BlobId.of(reference.bucket(), reference.key()));
        } catch (StorageException exception) {
            throw new ObjectStorageException(
                    ObjectStorageException.Reason.UPSTREAM_FAILURE,
                    "failed to remove object: " + reference.key(),
                    exception
            );
        }
    }
}
