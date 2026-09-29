package com.terraformers.modernization.storage;

public interface ObjectWriter {

    /** A successful result must preserve the bucket and key supplied in the request. */
    ObjectWriteResult writeText(ObjectWriteRequest request);

    default ObjectWriteResult writeBytes(ObjectBinaryWriteRequest request) {
        throw new UnsupportedOperationException("binary object writes are not supported");
    }
}
