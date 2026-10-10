package com.terraformers.modernization.storage;

public interface ObjectReader {

    default boolean isAvailable() {
        return true;
    }

    ObjectMetadata readMetadata(ObjectReference reference);

    ObjectContent readContent(ObjectReference reference);

    default ObjectContent readContent(ObjectReference reference, int maxBytes) {
        if (readMetadata(reference).contentLength() > maxBytes) throw new IllegalStateException("object size limit exceeded");
        ObjectContent content = readContent(reference);
        if (content.size() > maxBytes) throw new IllegalStateException("object size limit exceeded");
        return content;
    }
}
