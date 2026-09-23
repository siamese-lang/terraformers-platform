package com.terraformers.modernization.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(prefix = "terraformers.storage", name = "s3-reader-enabled", havingValue = "true")
public class AwsS3ObjectReader implements ObjectReader {

    private final S3Client s3Client;

    public AwsS3ObjectReader() {
        this.s3Client = S3Client.builder().build();
    }

    AwsS3ObjectReader(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public ObjectMetadata readMetadata(ObjectReference reference) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(reference.bucket()).key(reference.key()).build());
            return new ObjectMetadata(reference.bucket(), reference.key(), response.contentType(),
                    response.contentLength(), response.eTag(), response.lastModified());
        } catch (S3Exception exception) {
            throw translate(reference, exception);
        }
    }

    @Override
    public ObjectContent readContent(ObjectReference reference) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(reference.bucket()).key(reference.key()).build());
            ObjectMetadata metadata = new ObjectMetadata(reference.bucket(), reference.key(),
                    response.response().contentType(), response.response().contentLength(),
                    response.response().eTag(), response.response().lastModified());
            return new ObjectContent(metadata, response.asByteArray());
        } catch (S3Exception exception) {
            throw translate(reference, exception);
        }
    }

    private ObjectStorageException translate(ObjectReference reference, S3Exception exception) {
        ObjectStorageException.Reason reason = exception.statusCode() == 404
                ? ObjectStorageException.Reason.NOT_FOUND
                : ObjectStorageException.Reason.UPSTREAM_FAILURE;
        return new ObjectStorageException(reason, "failed to read object: " + reference.key(), exception);
    }
}
