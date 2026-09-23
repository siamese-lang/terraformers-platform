package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class AwsS3ObjectReaderTest {
    @Test
    void mapsS3MetadataToNeutralMetadata() {
        S3Client client = mock(S3Client.class);
        Instant modified = Instant.parse("2026-07-15T00:00:00Z");
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/png").contentLength(12L).eTag("etag").lastModified(modified).build());
        ObjectMetadata result = new AwsS3ObjectReader(client).readMetadata(new ObjectReference("bucket", "key"));
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.contentLength()).isEqualTo(12L);
        assertThat(result.eTag()).isEqualTo("etag");
        assertThat(result.lastModified()).isEqualTo(modified);
    }

    @Test
    void translatesS3NotFoundToNeutralError() {
        S3Client client = mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(() -> new AwsS3ObjectReader(client).readMetadata(new ObjectReference("bucket", "key")))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(ObjectStorageException.Reason.NOT_FOUND));
    }
}
