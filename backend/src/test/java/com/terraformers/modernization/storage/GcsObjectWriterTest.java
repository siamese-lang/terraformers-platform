package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GcsObjectWriterTest {

    @Test
    void writesTextAndPreservesLogicalIdentity() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        when(blob.getEtag()).thenReturn("provider-etag");
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(blob);

        GcsObjectWriter writer = new GcsObjectWriter(storage);
        ObjectWriteResult result = writer.writeText(new ObjectWriteRequest(
                "result-bucket",
                "analysis-results/job-1/result.tf",
                "resource \"aws_vpc\" \"main\" {}",
                "text/plain"
        ));

        ArgumentCaptor<BlobInfo> infoCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(storage).create(infoCaptor.capture(), bytesCaptor.capture());

        assertThat(infoCaptor.getValue().getBlobId())
                .isEqualTo(BlobId.of("result-bucket", "analysis-results/job-1/result.tf"));
        assertThat(infoCaptor.getValue().getContentType()).isEqualTo("text/plain");
        assertThat(new String(bytesCaptor.getValue(), StandardCharsets.UTF_8))
                .isEqualTo("resource \"aws_vpc\" \"main\" {}");
        assertThat(result.provider()).isEqualTo("gcs");
        assertThat(result.persisted()).isTrue();
        assertThat(result.bucket()).isEqualTo("result-bucket");
        assertThat(result.key()).isEqualTo("analysis-results/job-1/result.tf");
        assertThat(result.eTag()).isEqualTo("provider-etag");
    }

    @Test
    void binaryOverwriteUsesSameBucketAndKey() {
        Storage storage = mock(Storage.class);
        Blob first = mock(Blob.class);
        Blob second = mock(Blob.class);
        when(first.getEtag()).thenReturn("etag-1");
        when(second.getEtag()).thenReturn("etag-2");
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(first, second);

        GcsObjectWriter writer = new GcsObjectWriter(storage);
        ObjectBinaryWriteRequest firstRequest = new ObjectBinaryWriteRequest(
                "source-bucket", "browser-uploads/job-1/source.webp", new byte[] {1, 2}, "image/webp");
        ObjectBinaryWriteRequest secondRequest = new ObjectBinaryWriteRequest(
                "source-bucket", "browser-uploads/job-1/source.webp", new byte[] {3, 4}, "image/webp");

        writer.writeBytes(firstRequest);
        ObjectWriteResult overwritten = writer.writeBytes(secondRequest);

        ArgumentCaptor<BlobInfo> infoCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage, org.mockito.Mockito.times(2)).create(infoCaptor.capture(), any(byte[].class));
        List<BlobInfo> infos = infoCaptor.getAllValues();
        assertThat(infos).extracting(BlobInfo::getBlobId)
                .containsExactly(
                        BlobId.of("source-bucket", "browser-uploads/job-1/source.webp"),
                        BlobId.of("source-bucket", "browser-uploads/job-1/source.webp")
                );
        assertThat(overwritten.eTag()).isEqualTo("etag-2");
    }

    @Test
    void removeSucceedsWhenObjectIsAlreadyAbsent() {
        Storage storage = mock(Storage.class);
        ObjectReference reference = new ObjectReference("result-bucket", "analysis-results/missing.tf");
        BlobId blobId = BlobId.of(reference.bucket(), reference.key());
        when(storage.delete(blobId)).thenReturn(false);

        assertThatCode(() -> new GcsObjectWriter(storage).remove(reference)).doesNotThrowAnyException();
        verify(storage).delete(blobId);
    }

    @Test
    void mapsWriteFailureToUpstreamFailure() {
        Storage storage = mock(Storage.class);
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new StorageException(503, "unavailable"));

        assertThatThrownBy(() -> new GcsObjectWriter(storage).writeBytes(new ObjectBinaryWriteRequest(
                "result-bucket", "analysis-results/job-1/result.tf", new byte[] {1}, "text/plain")))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ObjectStorageException.Reason.UPSTREAM_FAILURE));
    }

    @Test
    void mapsDeleteFailureToUpstreamFailure() {
        Storage storage = mock(Storage.class);
        ObjectReference reference = new ObjectReference("result-bucket", "analysis-results/job-1/result.tf");
        BlobId blobId = BlobId.of(reference.bucket(), reference.key());
        when(storage.delete(blobId)).thenThrow(new StorageException(503, "unavailable"));

        assertThatThrownBy(() -> new GcsObjectWriter(storage).remove(reference))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ObjectStorageException.Reason.UPSTREAM_FAILURE));
    }
}
