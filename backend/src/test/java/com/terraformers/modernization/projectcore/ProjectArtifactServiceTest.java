package com.terraformers.modernization.projectcore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.analysis.AnalysisJobRepository;
import com.terraformers.modernization.storage.ObjectWriteResult;
import com.terraformers.modernization.storage.ObjectWriter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
