package com.terraformers.modernization.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "terraformers.storage", name = "writer-provider", havingValue = "metadata-only", matchIfMissing = true)
public class StubObjectWriter implements ObjectWriter {

    @Override
    public ObjectWriteResult writeText(ObjectWriteRequest request) {
        return new ObjectWriteResult(
                "metadata-only",
                false,
                request.bucket(),
                request.key(),
                null
        );
    }

    @Override
    public ObjectWriteResult writeBytes(ObjectBinaryWriteRequest request) {
        return new ObjectWriteResult("metadata-only", false, request.bucket(), request.key(), null);
    }
}
