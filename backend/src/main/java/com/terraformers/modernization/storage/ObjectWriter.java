package com.terraformers.modernization.storage;

public interface ObjectWriter {

    ObjectWriteResult writeText(ObjectWriteRequest request);

    default ObjectWriteResult writeBytes(ObjectBinaryWriteRequest request) {
        throw new UnsupportedOperationException("binary object writes are not supported");
    }
}
