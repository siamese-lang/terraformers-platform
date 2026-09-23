package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class UploadObjectStorageServiceTest {

    @Test
    void metadataOnlyWriterReturnsLogicalReference() {
        ObjectWriter writer = mock(ObjectWriter.class);
        when(writer.writeBytes(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ObjectBinaryWriteRequest request = invocation.getArgument(0);
            return new ObjectWriteResult("metadata-only", false, request.bucket(), request.key(), null);
        });
        UploadObjectStorageService service = new UploadObjectStorageService(writer, "example-bucket", "/browser-uploads/");

        StoredUploadObject result = service.store(imageFile(), "aws", "AWS아키텍처.png");

        assertThat(result.provider()).isEqualTo("metadata-only");
        assertThat(result.binaryPersisted()).isFalse();
        assertThat(result.bucket()).isEqualTo("example-bucket");
        assertThat(result.key()).startsWith("browser-uploads/aws/").endsWith("AWS-.png");
        assertThat(result.eTag()).isNull();
    }

    @Test
    void persistedWriterResultAndBinaryRequestArePassedThrough() {
        ObjectWriter writer = mock(ObjectWriter.class);
        when(writer.writeBytes(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ObjectBinaryWriteRequest request = invocation.getArgument(0);
            return new ObjectWriteResult("custom-store", true, request.bucket(), request.key(), "etag-123");
        });
        UploadObjectStorageService service = new UploadObjectStorageService(writer, "upload-bucket", "browser-uploads");

        StoredUploadObject result = service.store(imageFile(), "aws", "diagram.png");

        ArgumentCaptor<ObjectBinaryWriteRequest> captor = ArgumentCaptor.forClass(ObjectBinaryWriteRequest.class);
        verify(writer).writeBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("upload-bucket");
        assertThat(captor.getValue().key()).startsWith("browser-uploads/aws/");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(captor.getValue().bytes()).isEqualTo("fake image bytes".getBytes());
        assertThat(result.provider()).isEqualTo("custom-store");
        assertThat(result.binaryPersisted()).isTrue();
        assertThat(result.eTag()).isEqualTo("etag-123");
    }

    @Test
    void writerFailureRetainsUploadStorageException() {
        ObjectWriter writer = mock(ObjectWriter.class);
        when(writer.writeBytes(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("failed"));
        UploadObjectStorageService service = new UploadObjectStorageService(writer, "bucket", "prefix");
        assertThatThrownBy(() -> service.store(imageFile(), "1", "diagram.png"))
                .isInstanceOf(UploadStorageException.class);
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "diagram.png", "image/png", "fake image bytes".getBytes());
    }
}
