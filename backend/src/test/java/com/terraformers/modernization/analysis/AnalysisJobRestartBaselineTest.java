package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.TerraformersBackendApplication;
import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.identity.UserRepository;
import com.terraformers.modernization.project.ProjectVisibility;
import com.terraformers.modernization.projectcore.OwnedProjectEntity;
import com.terraformers.modernization.projectcore.OwnedProjectRepository;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.projectcore.ProjectFileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Primary;

class AnalysisJobRestartBaselineTest {

    @Test
    void beforeStateAcceptedButNotStartedJobIsFailedWhenApplicationRestarts() {
        String datasourceUrl = datasourceUrl();
        String jobId;

        try (ConfigurableApplicationContext first = startApplicationWithCapturingExecutor(datasourceUrl)) {
            UserEntity owner = persistOwner(first);
            OwnedProjectEntity project = persistProject(first, owner);
            ProjectFileEntity source = persistSource(first, owner, project);

            AnalysisJobResponse accepted = first.getBean(AnalysisJobService.class).create(
                    new AnalysisJobRequest(project.getProjectId(), source.getFileId(), "accepted-not-started"),
                    owner
            );
            jobId = accepted.id();

            assertThat(first.getBean(CapturingExecutor.class).tasks()).hasSize(1);
            assertThat(first.getBean(AnalysisJobRepository.class).findById(jobId))
                    .get()
                    .extracting(AnalysisJobEntity::getStatus)
                    .isEqualTo(AnalysisJobStatus.PENDING);
        }

        try (ConfigurableApplicationContext restarted = startApplication(datasourceUrl, "none")) {
            assertInterrupted(restarted.getBean(AnalysisJobRepository.class), jobId);
        }
    }

    @Test
    void beforeStateClaimedRunningJobIsFailedWhenApplicationRestarts() {
        String datasourceUrl = datasourceUrl();
        String jobId;

        try (ConfigurableApplicationContext first = startApplication(datasourceUrl, "create")) {
            AnalysisJobRepository repository = first.getBean(AnalysisJobRepository.class);
            AnalysisJobEntity pending = repository.saveAndFlush(job(AnalysisJobStatus.PENDING, "claimed-running"));
            jobId = pending.getId();

            assertThat(first.getBean(AnalysisJobStateService.class).claimPending(jobId)).isPresent();
            assertThat(repository.findById(jobId))
                    .get()
                    .extracting(AnalysisJobEntity::getStatus)
                    .isEqualTo(AnalysisJobStatus.RUNNING);
        }

        try (ConfigurableApplicationContext restarted = startApplication(datasourceUrl, "none")) {
            assertInterrupted(restarted.getBean(AnalysisJobRepository.class), jobId);
        }
    }

    @Test
    void persistedNonTerminalJobsAreFailedAcrossApplicationRestartWhileTerminalJobsRemainTerminal() {
        String databaseName = "terraformers-restart-" + UUID.randomUUID().toString().replace("-", "");
        String datasourceUrl = "jdbc:h2:mem:" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        String pendingJobId;
        String runningJobId;
        String succeededJobId;
        String failedJobId;

        try (ConfigurableApplicationContext first = startApplication(datasourceUrl, "create")) {
            AnalysisJobRepository repository = first.getBean(AnalysisJobRepository.class);

            AnalysisJobEntity pending = repository.saveAndFlush(job(AnalysisJobStatus.PENDING, "restart-pending"));
            AnalysisJobEntity running = repository.saveAndFlush(job(AnalysisJobStatus.RUNNING, "restart-running"));
            AnalysisJobEntity succeeded = repository.saveAndFlush(job(AnalysisJobStatus.SUCCEEDED, "restart-succeeded"));
            AnalysisJobEntity failed = job(AnalysisJobStatus.FAILED, "restart-failed");
            failed.setFailureReason("existing failure");
            failed = repository.saveAndFlush(failed);

            pendingJobId = pending.getId();
            runningJobId = running.getId();
            succeededJobId = succeeded.getId();
            failedJobId = failed.getId();
        }

        try (ConfigurableApplicationContext restarted = startApplication(datasourceUrl, "none")) {
            AnalysisJobRepository repository = restarted.getBean(AnalysisJobRepository.class);

            assertInterrupted(repository, pendingJobId);
            assertInterrupted(repository, runningJobId);

            assertThat(repository.findById(succeededJobId))
                    .get()
                    .extracting(AnalysisJobEntity::getStatus)
                    .isEqualTo(AnalysisJobStatus.SUCCEEDED);
            assertThat(repository.findById(failedJobId))
                    .get()
                    .satisfies(found -> {
                        assertThat(found.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
                        assertThat(found.getFailureReason()).isEqualTo("existing failure");
                    });
        }
    }

    private void assertInterrupted(AnalysisJobRepository repository, String jobId) {
        assertThat(repository.findById(jobId))
                .get()
                .satisfies(found -> {
                    assertThat(found.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
                    assertThat(found.getFailureReason())
                            .isEqualTo(AnalysisJobRestartReconciler.INTERRUPTED_FAILURE_REASON);
                });
    }

    private ConfigurableApplicationContext startApplication(String datasourceUrl, String ddlAuto) {
        return new SpringApplicationBuilder(TerraformersBackendApplication.class)
                .profiles("test")
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + datasourceUrl,
                        "--spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                        "--spring.flyway.enabled=false",
                        "--terraformers.security.jwt.enabled=false"
                );
    }

    private ConfigurableApplicationContext startApplicationWithCapturingExecutor(String datasourceUrl) {
        return new SpringApplicationBuilder(TerraformersBackendApplication.class, CapturingExecutorConfig.class)
                .profiles("test")
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + datasourceUrl,
                        "--spring.jpa.hibernate.ddl-auto=create",
                        "--spring.flyway.enabled=false",
                        "--terraformers.security.jwt.enabled=false"
                );
    }

    private String datasourceUrl() {
        String databaseName = "terraformers-restart-" + UUID.randomUUID().toString().replace("-", "");
        return "jdbc:h2:mem:" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    private UserEntity persistOwner(ConfigurableApplicationContext context) {
        UserEntity owner = new UserEntity();
        owner.setExternalIdentity("test", "restart-owner-" + UUID.randomUUID());
        owner.setEmail("restart-owner-" + UUID.randomUUID() + "@example.test");
        owner.setDisplayName("Restart Owner");
        return context.getBean(UserRepository.class).saveAndFlush(owner);
    }

    private OwnedProjectEntity persistProject(ConfigurableApplicationContext context, UserEntity owner) {
        OwnedProjectEntity project = new OwnedProjectEntity();
        project.setOwner(owner);
        project.setName("Restart Boundary Project");
        project.setVisibility(ProjectVisibility.PRIVATE);
        return context.getBean(OwnedProjectRepository.class).saveAndFlush(project);
    }

    private ProjectFileEntity persistSource(
            ConfigurableApplicationContext context,
            UserEntity owner,
            OwnedProjectEntity project
    ) {
        ProjectFileEntity source = new ProjectFileEntity();
        source.setProject(project);
        source.setUploadedBy(owner);
        source.setNodeType("FILE");
        source.setFileType("ARCHITECTURE_IMAGE");
        source.setPath("source/restart.png");
        source.setS3Bucket("restart-source");
        source.setS3Key("source/restart.png");
        source.setContentType("image/png");
        source.setSizeBytes(1L);
        return context.getBean(ProjectFileRepository.class).saveAndFlush(source);
    }

    private AnalysisJobEntity job(AnalysisJobStatus status, String correlationId) {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(501L);
        entity.setSourceFileId(601L);
        entity.setSourceBucket("restart-baseline-bucket");
        entity.setSourceKey("source/restart-baseline.png");
        entity.setCorrelationId(correlationId);
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        entity.setStatus(status);
        return entity;
    }

    @TestConfiguration
    static class CapturingExecutorConfig {
        @Bean
        @Primary
        @Qualifier("analysisJobExecutor")
        CapturingExecutor capturingExecutor() {
            return new CapturingExecutor();
        }
    }

    static class CapturingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        List<Runnable> tasks() {
            return List.copyOf(tasks);
        }
    }
}
