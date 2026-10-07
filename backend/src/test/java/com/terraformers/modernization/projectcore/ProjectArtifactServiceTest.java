package com.terraformers.modernization.projectcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisJobRepository;
import com.terraformers.modernization.analysis.AnalysisJobEntity;
import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.ObjectWriter;
import java.util.List;
import java.util.Optional;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectArtifactServiceTest {

    @Test
    void generatedTerraformUsesExplicitMetadataOnlySemantics() {
        ProjectFileEntity file = service().registerGeneratedTerraform(42L, "content",
                new ObjectWriteResult("metadata-only", false, "bucket", "main.tf", null));
        assertThat(file.getStorageProvider()).isEqualTo("metadata-only");
        assertThat(file.isBinaryPersisted()).isFalse();
    }

    @Test
    void generatedTerraformUsesExplicitPersistedSemanticsEvenWithoutEtag() {
        ProjectFileEntity file = service().registerGeneratedTerraform(42L, "content",
                new ObjectWriteResult("s3", true, "bucket", "main.tf", null));
        assertThat(file.getStorageProvider()).isEqualTo("s3");
        assertThat(file.isBinaryPersisted()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"true, true", "true, false", "false, true", "false, false"})
    void latestJobArtifactsRejectForeignProjectOrMissingActiveFile(boolean source, boolean foreignProject) {
        Fixture fixture = jobArtifactFixture(foreignProject ? 99L : 42L, !foreignProject);
        assertThatThrownBy(() -> {
            if (source) {
                fixture.service().requireLatestJobSourceImage(42L);
            } else {
                fixture.service().requireLatestJobTerraform(42L);
            }
        }).isInstanceOf(NoSuchElementException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void latestJobArtifactsReturnOnlyTheBoundActiveProjectFile(boolean source) {
        Fixture fixture = jobArtifactFixture(42L, false);
        ProjectFileEntity file = source ? fixture.service().requireLatestJobSourceImage(42L)
                : fixture.service().requireLatestJobTerraform(42L);
        assertThat(file).isSameAs(fixture.file());
    }

    private Fixture jobArtifactFixture(Long fileProjectId, boolean missingFile) {
        ProjectFileRepository files = mock(ProjectFileRepository.class);
        AnalysisJobRepository jobs = mock(AnalysisJobRepository.class);
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(42L);
        job.setSourceFileId(101L);
        job.setResultFileId(101L);
        when(jobs.findFirstByProjectIdOrderByCreatedAtDesc(42L)).thenReturn(Optional.of(job));
        OwnedProjectEntity project = mock(OwnedProjectEntity.class);
        when(project.getProjectId()).thenReturn(fileProjectId);
        ProjectFileEntity file = mock(ProjectFileEntity.class);
        when(file.getProject()).thenReturn(project);
        when(files.findByFileIdAndDeletedAtIsNull(101L)).thenReturn(missingFile ? Optional.empty() : Optional.of(file));
        ProjectArtifactService service = new ProjectArtifactService(mock(OwnedProjectRepository.class), files,
                mock(ProjectDomainService.class), mock(ObjectWriter.class), jobs);
        return new Fixture(service, file);
    }

    private record Fixture(ProjectArtifactService service, ProjectFileEntity file) {}

    private ProjectArtifactService service() {
        OwnedProjectRepository projects = mock(OwnedProjectRepository.class);
        ProjectFileRepository files = mock(ProjectFileRepository.class);
        when(projects.findByProjectIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(new OwnedProjectEntity()));
        when(files.findByProject_ProjectIdAndFileTypeAndDeletedAtIsNullOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(files.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new ProjectArtifactService(projects, files, mock(ProjectDomainService.class),
                mock(ObjectWriter.class), mock(AnalysisJobRepository.class));
    }
}
