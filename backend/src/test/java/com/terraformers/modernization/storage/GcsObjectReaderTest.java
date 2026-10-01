package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GcsObjectReaderTest {

    @Test
    void readsMetadataWithoutReadingObjectBytes() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        ObjectReference reference = new ObjectReference("source-bucket", "browser-uploads/architecture.webp");
        BlobId blobId = BlobId.of(reference.bucket(), reference.key());
        OffsetDateTime updated = OffsetDateTime.of(2026, 10, 1, 6, 0, 0, 0, ZoneOffset.UTC);

        when(storage.get(blobId)).thenReturn(blob);
        when(blob.getContentType()).thenReturn("image/webp");
        when(blob.getSize()).thenReturn(3L);
        when(blob.getEtag()).thenReturn("provider-etag");
        when(blob.getUpdateTimeOffsetDateTime()).thenReturn(updated);

        ObjectMetadata metadata = new GcsObjectReader(storage).readMetadata(reference);

        assertThat(metadata.bucket()).isEqualTo(reference.bucket());
        assertThat(metadata.key()).isEqualTo(reference.key());
        assertThat(metadata.contentType()).isEqualTo("image/webp");
        assertThat(metadata.contentLength()).isEqualTo(3L);
        assertThat(metadata.eTag()).isEqualTo("provider-etag");
        assertThat(metadata.lastModified()).isEqualTo(updated.toInstant());
        verify(blob, never()).getContent();
    }

    @Test
    void readsExactBytesWithPortableMetadata() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        ObjectReference reference = new ObjectReference("source-bucket", "browser-uploads/architecture.webp");
        BlobId blobId = BlobId.of(reference.bucket(), reference.key());
        byte[] bytes = new byte[] {0x01, 0x02, (byte) 0xff};

        when(storage.get(blobId)).thenReturn(blob);
        when(blob.getContentType()).thenReturn("image/webp");
        when(blob.getSize()).thenReturn((long) bytes.length);
        when(blob.getEtag()).thenReturn("provider-etag");
        when(blob.getContent()).thenReturn(bytes);

        ObjectContent content = new GcsObjectReader(storage).readContent(reference);

        assertThat(content.metadata().bucket()).isEqualTo(reference.bucket());
        assertThat(content.metadata().key()).isEqualTo(reference.key());
        assertThat(content.bytes()).containsExactly(bytes);
    }

    @Test
    void mapsAbsentObjectToNotFound() {
        Storage storage = mock(Storage.class);
        ObjectReference reference = new ObjectReference("source-bucket", "missing.webp");
        when(storage.get(BlobId.of(reference.bucket(), reference.key()))).thenReturn(null);

        assertThatThrownBy(() -> new GcsObjectReader(storage).readMetadata(reference))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(ObjectStorageException.Reason.NOT_FOUND));
    }

    @Test
    void mapsStorage404ToNotFound() {
        Storage storage = mock(Storage.class);
        ObjectReference reference = new ObjectReference("source-bucket", "missing.webp");
        when(storage.get(BlobId.of(reference.bucket(), reference.key())))
                .thenThrow(new StorageException(404, "not found"));

        assertThatThrownBy(() -> new GcsObjectReader(storage).readContent(reference))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(ObjectStorageException.Reason.NOT_FOUND));
    }

    @Test
    void mapsOtherStorageFailureToUpstreamFailure() {
        Storage storage = mock(Storage.class);
        ObjectReference reference = new ObjectReference("source-bucket", "source.webp");
        when(storage.get(BlobId.of(reference.bucket(), reference.key())))
                .thenThrow(new StorageException(503, "unavailable"));

        assertThatThrownBy(() -> new GcsObjectReader(storage).readContent(reference))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ObjectStorageException.Reason.UPSTREAM_FAILURE));
    }
}
