package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class StubObjectWriterTest {
    @Test
    void textAndBinaryWritesAreExplicitlyMetadataOnly() {
        StubObjectWriter writer = new StubObjectWriter();
        ObjectWriteResult text = writer.writeText(new ObjectWriteRequest("bucket", "main.tf", "content", "text/plain"));
        ObjectWriteResult binary = writer.writeBytes(new ObjectBinaryWriteRequest("bucket", "image.png", new byte[] {1}, "image/png"));
        assertMetadataOnly(text, "main.tf");
        assertMetadataOnly(binary, "image.png");
    }

    private void assertMetadataOnly(ObjectWriteResult result, String key) {
        assertThat(result.provider()).isEqualTo("metadata-only");
        assertThat(result.persisted()).isFalse();
        assertThat(result.bucket()).isEqualTo("bucket");
        assertThat(result.key()).isEqualTo(key);
        assertThat(result.eTag()).isNull();
    }
}
