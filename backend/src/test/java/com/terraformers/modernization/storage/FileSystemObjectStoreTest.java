package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemObjectStoreTest {

    @TempDir Path root;

    @Test
    void writesAndReadsExactBinaryContentAndMetadata() {
        FileSystemObjectStore store = new FileSystemObjectStore(root.toString());
        byte[] bytes = {0, 1, 2, -1};

        ObjectWriteResult result = store.writeBytes(new ObjectBinaryWriteRequest("bucket", "nested/image.png", bytes, "image/png"));
        ObjectContent content = store.readContent(new ObjectReference("bucket", "nested/image.png"));

        assertThat(result.provider()).isEqualTo("filesystem");
        assertThat(result.persisted()).isTrue();
        assertThat(result.bucket()).isEqualTo("bucket");
        assertThat(result.key()).isEqualTo("nested/image.png");
        assertThat(result.eTag()).isEqualTo("6caf38d537984e2601ea8353ab4e3f289d4b9a073139b747a9d149b3e26cf1d4");
        assertThat(content.bytes()).containsExactly(bytes);
        assertThat(content.metadata().contentLength()).isEqualTo(bytes.length);
        assertThat(content.metadata().contentType()).isEqualTo("image/png");
        assertThat(content.metadata().eTag()).isEqualTo(result.eTag());
        assertThat(content.metadata().lastModified()).isNotNull();
    }

    @Test
    void writesTextAsExactUtf8Bytes() {
        FileSystemObjectStore store = new FileSystemObjectStore(root.toString());
        String text = "resource \"example\" \"테스트\" {}\n";
        store.writeText(new ObjectWriteRequest("bucket", "result/main.tf", text, "text/plain"));

        assertThat(store.readContent(new ObjectReference("bucket", "result/main.tf")).bytes())
                .containsExactly(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void reportsMissingObjectAsNotFound() {
        FileSystemObjectStore store = new FileSystemObjectStore(root.toString());
        assertThatThrownBy(() -> store.readContent(new ObjectReference("bucket", "missing")))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(ObjectStorageException.Reason.NOT_FOUND));
    }

    @Test
    void rejectsParentTraversalOutsideBucketRoot() {
        FileSystemObjectStore store = new FileSystemObjectStore(root.toString());
        assertThatThrownBy(() -> store.writeBytes(new ObjectBinaryWriteRequest("bucket", "nested/../../escape", new byte[0], "x")))
                .isInstanceOf(ObjectStorageException.class);
        assertThat(root.resolve("escape")).doesNotExist();
    }

    @Test
    void rejectsAbsoluteBucketAndKeyEscapes() {
        FileSystemObjectStore store = new FileSystemObjectStore(root.toString());
        assertThatThrownBy(() -> store.readMetadata(new ObjectReference(root.resolve("../escape").toString(), "object")))
                .isInstanceOf(ObjectStorageException.class);
        assertThatThrownBy(() -> store.writeBytes(new ObjectBinaryWriteRequest("bucket", root.resolve("../escape").toString(), new byte[0], "x")))
                .isInstanceOf(ObjectStorageException.class);
    }
}
