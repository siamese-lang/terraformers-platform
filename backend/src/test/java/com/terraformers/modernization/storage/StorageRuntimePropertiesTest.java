package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StorageRuntimePropertiesTest {

    @Test
    void resolvesCanonicalStorageSelectors() {
        StorageRuntimeProperties properties = new StorageRuntimeProperties();
        assertThat(properties.resolvedReaderProvider()).isEqualTo("disabled");
        assertThat(properties.resolvedWriterProvider()).isEqualTo("metadata-only");

        properties.setReaderProvider(" S3 ");
        properties.setWriterProvider("s3");
        assertThat(properties.resolvedReaderProvider()).isEqualTo("s3");
        assertThat(properties.resolvedWriterProvider()).isEqualTo("s3");

        properties.setReaderProvider("filesystem");
        properties.setWriterProvider("filesystem");
        assertThat(properties.resolvedReaderProvider()).isEqualTo("filesystem");
        assertThat(properties.resolvedWriterProvider()).isEqualTo("filesystem");
    }

    @Test
    void rejectsUnsupportedStorageSelectors() {
        StorageRuntimeProperties properties = new StorageRuntimeProperties();
        properties.setReaderProvider("unknown");
        assertThatThrownBy(properties::resolvedReaderProvider)
                .hasMessageContaining("terraformers.storage.reader-provider");
        properties.setWriterProvider("unknown");
        assertThatThrownBy(properties::resolvedWriterProvider)
                .hasMessageContaining("terraformers.storage.writer-provider");
    }
}
