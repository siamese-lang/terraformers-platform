package com.terraformers.modernization.storage;

public interface ObjectReader {

    default boolean isAvailable() {
        return true;
    }

    ObjectMetadata readMetadata(ObjectReference reference);

    ObjectContent readContent(ObjectReference reference);
}
