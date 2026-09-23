package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class AwsS3ObjectWriterTest {
    @Test
    void writesTextAndBinaryWithExplicitPersistedSemantics() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("etag").build());
        AwsS3ObjectWriter writer = new AwsS3ObjectWriter(client);
        ObjectWriteResult text = writer.writeText(new ObjectWriteRequest("bucket", "main.tf", "abc", "text/plain"));
        ObjectWriteResult binary = writer.writeBytes(new ObjectBinaryWriteRequest("bucket", "image.png", new byte[] {1, 2}, "image/png"));
        assertPersisted(text, "main.tf");
        assertPersisted(binary, "image.png");
        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, org.mockito.Mockito.times(2)).putObject(requests.capture(), any(RequestBody.class));
        assertThat(requests.getAllValues().get(0).contentLength()).isEqualTo(3);
        assertThat(requests.getAllValues().get(1).contentLength()).isEqualTo(2);
        assertThat(requests.getAllValues().get(1).contentType()).isEqualTo("image/png");
    }

    private void assertPersisted(ObjectWriteResult result, String key) {
        assertThat(result.provider()).isEqualTo("s3");
        assertThat(result.persisted()).isTrue();
        assertThat(result.bucket()).isEqualTo("bucket");
        assertThat(result.key()).isEqualTo(key);
        assertThat(result.eTag()).isEqualTo("etag");
    }
}
