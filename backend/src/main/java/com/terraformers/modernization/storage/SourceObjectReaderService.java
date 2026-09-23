package com.terraformers.modernization.storage;

import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.projectcore.ProjectArtifactService;
import com.terraformers.modernization.projectcore.ProjectDomainService;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SourceObjectReaderService {

    private final ProjectDomainService projectDomainService;
    private final ProjectArtifactService projectArtifactService;
    private final ObjectReader objectReader;

    public SourceObjectReaderService(ProjectDomainService projectDomainService,
            ProjectArtifactService projectArtifactService, ObjectReader objectReader) {
        this.projectDomainService = projectDomainService;
        this.projectArtifactService = projectArtifactService;
        this.objectReader = objectReader;
    }

    @Transactional(readOnly = true)
    public SourceObjectReadResponse read(Long projectId, UserEntity currentUser) {
        ProjectFileEntity sourceFile = requireReadableSource(projectId, currentUser, "project source object is metadata-only or missing: ");
        ObjectMetadata metadata = readMetadata(reference(sourceFile));
        return new SourceObjectReadResponse(projectId, sourceFile.getFileId(), metadata.bucket(), metadata.key(),
                sourceFile.getStorageProvider(), sourceFile.isBinaryPersisted(), sourceFile.getStorageETag(),
                metadata.eTag(), metadata.contentLength(), metadata.contentType(), metadata.lastModified());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> readImageContent(Long projectId, UserEntity currentUser) {
        ProjectFileEntity sourceFile = requireReadableSource(projectId, currentUser, "project source image content is unavailable: ");
        ObjectContent content;
        try {
            content = objectReader.readContent(reference(sourceFile));
        } catch (ObjectStorageException exception) {
            throw responseException(exception, sourceFile.getS3Key());
        }
        String contentType = content.metadata().contentType() == null ? sourceFile.getContentType() : content.metadata().contentType();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.size()))
                .contentType(MediaType.parseMediaType(contentType == null || contentType.isBlank()
                        ? "application/octet-stream" : contentType)).body(content.bytes());
    }

    private ProjectFileEntity requireReadableSource(Long projectId, UserEntity currentUser, String message) {
        projectDomainService.requireAccessibleProject(projectId, currentUser);
        ProjectFileEntity sourceFile = projectArtifactService.requireLatestJobSourceImage(projectId);
        if (!sourceFile.isBinaryPersisted() || isBlank(sourceFile.getS3Bucket()) || isBlank(sourceFile.getS3Key())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message + projectId);
        }
        if (!objectReader.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "object reader is unavailable");
        }
        return sourceFile;
    }

    private ObjectMetadata readMetadata(ObjectReference reference) {
        try {
            return objectReader.readMetadata(reference);
        } catch (ObjectStorageException exception) {
            throw responseException(exception, reference.key());
        }
    }

    private ResponseStatusException responseException(ObjectStorageException exception, String key) {
        HttpStatus status = switch (exception.reason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UPSTREAM_FAILURE -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, "failed to read source object: " + key, exception);
    }

    private ObjectReference reference(ProjectFileEntity file) {
        return new ObjectReference(file.getS3Bucket(), file.getS3Key());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
