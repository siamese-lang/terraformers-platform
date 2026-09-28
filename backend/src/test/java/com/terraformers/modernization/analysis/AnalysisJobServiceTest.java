package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.projectcore.OwnedProjectEntity;
import com.terraformers.modernization.projectcore.ProjectDomainService;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.projectcore.ProjectFileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisJobServiceTest {

    @Test
    void committedJobGetsBestEffortDispatcherNudgeAndRemainsPending() {
        AnalysisJobRepository repository = mock(AnalysisJobRepository.class);
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        AnalysisJobDispatcher dispatcher = mock(AnalysisJobDispatcher.class);
        ProjectDomainService projectDomainService = mock(ProjectDomainService.class);
        ProjectFileRepository projectFileRepository = mock(ProjectFileRepository.class);
        UserEntity requester = mock(UserEntity.class);
        OwnedProjectEntity project = mock(OwnedProjectEntity.class);
        ProjectFileEntity sourceFile = mock(ProjectFileEntity.class);
        when(project.getProjectId()).thenReturn(42L);
        when(sourceFile.getFileId()).thenReturn(101L);
        when(sourceFile.getProject()).thenReturn(project);
        when(sourceFile.getS3Bucket()).thenReturn("source-bucket");
        when(sourceFile.getS3Key()).thenReturn("source/key.png");
        when(projectDomainService.requireModifiableProject(42L, requester)).thenReturn(project);
        when(projectFileRepository.findById(101L)).thenReturn(Optional.of(sourceFile));
        when(repository.save(any(AnalysisJobEntity.class))).thenAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            entity.prePersist();
            return entity;
        });
        AnalysisJobService service = new AnalysisJobService(
                repository,
                properties,
                dispatcher,
                projectDomainService,
                projectFileRepository
        );

        AnalysisJobResponse response = service.create(new AnalysisJobRequest(42L, 101L, "reject-test"), requester);

        assertThat(response.status()).isEqualTo(AnalysisJobStatus.PENDING);
        verify(dispatcher).submit(response.id());
    }
}
