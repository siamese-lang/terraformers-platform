package com.terraformers.modernization.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "terraformers.storage", name = "writer-provider", havingValue = "metadata-only", matchIfMissing = true)
public class StubObjectWriter implements ObjectWriter, ObjectRemover {

    @Override
    public void remove(ObjectReference reference) {
        // Metadata-only writes persist no object bytes, so compensation is intentionally a no-op.
    }

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
