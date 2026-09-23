package com.terraformers.modernization.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.projectcore.ProjectArtifactService;
import com.terraformers.modernization.projectcore.ProjectDomainService;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SourceObjectReaderServiceTest {

    @Test
    void readsMetadataThroughObjectReader() {
        Fixture fixture = fixture(persistedSourceFile());
        Instant modified = Instant.parse("2026-07-15T00:00:00Z");
        when(fixture.reader.readMetadata(any())).thenReturn(new ObjectMetadata("bucket", "source.png", "image/png", 16, "read-etag", modified));
        SourceObjectReadResponse response = fixture.service.read(42L, null);
        verify(fixture.domain).requireAccessibleProject(42L, null);
        assertThat(response.s3ETag()).isEqualTo("read-etag");
        assertThat(response.contentLength()).isEqualTo(16);
        assertThat(response.lastModified()).isEqualTo(modified);
    }

    @Test
    void readsContentThroughObjectReader() {
        Fixture fixture = fixture(persistedSourceFile());
        byte[] bytes = {1, 2, 3};
        when(fixture.reader.readContent(any())).thenReturn(new ObjectContent(new ObjectMetadata("bucket", "source.png", "image/png", 3, "etag"), bytes));
        assertThat(fixture.service.readImageContent(42L, null).getBody()).containsExactly(bytes);
    }

    @Test
    void projectAccessIsCheckedBeforeObjectRead() {
        Fixture fixture = fixture(persistedSourceFile());
        UserEntity user = mock(UserEntity.class);
        org.mockito.Mockito.doThrow(new SecurityException("forbidden")).when(fixture.domain).requireAccessibleProject(42L, user);
        assertThatThrownBy(() -> fixture.service.read(42L, user)).isInstanceOf(SecurityException.class);
        verify(fixture.reader, never()).readMetadata(any());
    }

    @Test
    void metadataOnlySourceIsRejected() {
        Fixture fixture = fixture(metadataOnlySourceFile());
        assertStatus(() -> fixture.service.read(42L, null), HttpStatus.CONFLICT);
    }

    @Test
    void unavailableReaderIsServiceUnavailable() {
        Fixture fixture = fixture(persistedSourceFile());
        when(fixture.reader.isAvailable()).thenReturn(false);
        assertStatus(() -> fixture.service.read(42L, null), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void neutralReaderErrorsPreserveHttpBehavior() {
        assertReaderError(ObjectStorageException.Reason.NOT_FOUND, HttpStatus.NOT_FOUND);
        assertReaderError(ObjectStorageException.Reason.UPSTREAM_FAILURE, HttpStatus.BAD_GATEWAY);
        assertReaderError(ObjectStorageException.Reason.UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void assertReaderError(ObjectStorageException.Reason reason, HttpStatus status) {
        Fixture fixture = fixture(persistedSourceFile());
        when(fixture.reader.readMetadata(any())).thenThrow(new ObjectStorageException(reason, "failure", null));
        assertStatus(() -> fixture.service.read(42L, null), status);
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    private Fixture fixture(ProjectFileEntity file) {
        ProjectDomainService domain = mock(ProjectDomainService.class);
        ProjectArtifactService artifacts = mock(ProjectArtifactService.class);
        ObjectReader reader = mock(ObjectReader.class);
        when(reader.isAvailable()).thenReturn(true);
        when(artifacts.requireLatestJobSourceImage(42L)).thenReturn(file);
        return new Fixture(domain, reader, new SourceObjectReaderService(domain, artifacts, reader));
    }

    private ProjectFileEntity persistedSourceFile() {
        ProjectFileEntity file = mock(ProjectFileEntity.class);
        when(file.getFileId()).thenReturn(100L);
        when(file.getS3Bucket()).thenReturn("bucket");
        when(file.getS3Key()).thenReturn("source.png");
        when(file.getStorageProvider()).thenReturn("s3");
        when(file.isBinaryPersisted()).thenReturn(true);
        when(file.getStorageETag()).thenReturn("upload-etag");
        return file;
    }

    private ProjectFileEntity metadataOnlySourceFile() {
        ProjectFileEntity file = persistedSourceFile();
        when(file.isBinaryPersisted()).thenReturn(false);
        return file;
    }

    private record Fixture(ProjectDomainService domain, ObjectReader reader, SourceObjectReaderService service) {}
}
