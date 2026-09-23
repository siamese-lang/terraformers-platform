package com.terraformers.modernization.storage;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Component
@ConditionalOnProperty(prefix = "terraformers.storage", name = "writer-provider", havingValue = "s3")
public class AwsS3ObjectWriter implements ObjectWriter {

    private final S3Client s3Client;

    public AwsS3ObjectWriter() {
        this.s3Client = S3Client.builder().build();
    }

    AwsS3ObjectWriter(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public ObjectWriteResult writeText(ObjectWriteRequest request) {
        return writeBytes(new ObjectBinaryWriteRequest(
                request.bucket(), request.key(), request.content().getBytes(StandardCharsets.UTF_8), request.contentType()));
    }

    @Override
    public ObjectWriteResult writeBytes(ObjectBinaryWriteRequest request) {
        byte[] bytes = request.bytes();
        PutObjectResponse response = s3Client.putObject(PutObjectRequest.builder()
                .bucket(request.bucket())
                .key(request.key())
                .contentType(request.contentType())
                .contentLength((long) bytes.length)
                .build(), RequestBody.fromBytes(bytes));

        return new ObjectWriteResult("s3", true, request.bucket(), request.key(), response.eTag());
    }
}
