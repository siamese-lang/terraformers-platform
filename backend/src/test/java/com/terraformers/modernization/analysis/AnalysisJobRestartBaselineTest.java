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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

class AnalysisJobRestartBaselineTest {

    @Test
    void acceptedButNotStartedJobIsRecoveredByDurableScanAfterRestart() {
        String datasourceUrl = datasourceUrl();
        String jobId;
        try (ConfigurableApplicationContext first = start(datasourceUrl, "create")) {
            UserEntity owner = owner(first);
            OwnedProjectEntity project = project(first, owner);
            ProjectFileEntity source = source(first, owner, project);
            jobId = first.getBean(AnalysisJobService.class).create(
                    new AnalysisJobRequest(project.getProjectId(), source.getFileId(), "restart"), owner).id();
            assertThat(first.getBean(CapturingExecutor.class).tasks()).hasSize(1);
            assertThat(first.getBean(AnalysisJobRepository.class).findById(jobId).orElseThrow().getStatus())
                    .isEqualTo(AnalysisJobStatus.PENDING);
        }

        try (ConfigurableApplicationContext restarted = start(datasourceUrl, "none")) {
            AnalysisJobDispatcher dispatcher = restarted.getBean(AnalysisJobDispatcher.class);
            CapturingExecutor executor = restarted.getBean(CapturingExecutor.class);
            dispatcher.dispatchEligible();
            assertThat(executor.tasks()).isNotEmpty();
            executor.runAll();
            AnalysisJobEntity recovered = restarted.getBean(AnalysisJobRepository.class).findById(jobId).orElseThrow();
            assertThat(recovered.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
            assertThat(recovered.getAttemptCount()).isEqualTo(1);
            assertThat(recovered.getClaimGeneration()).isEqualTo(1);
        }
    }

    @Test
    void applicationStartupDoesNotFailRecoverableOrChangeTerminalJobs() {
        String datasourceUrl = datasourceUrl();
        String pendingId;
        String runningId;
        String succeededId;
        try (ConfigurableApplicationContext first = start(datasourceUrl, "create")) {
            AnalysisJobRepository repository = first.getBean(AnalysisJobRepository.class);
            pendingId = repository.saveAndFlush(job(AnalysisJobStatus.PENDING, "pending")).getId();
            runningId = repository.saveAndFlush(job(AnalysisJobStatus.RUNNING, "running-null-lease")).getId();
            succeededId = repository.saveAndFlush(job(AnalysisJobStatus.SUCCEEDED, "succeeded")).getId();
        }
        try (ConfigurableApplicationContext restarted = start(datasourceUrl, "none")) {
            AnalysisJobRepository repository = restarted.getBean(AnalysisJobRepository.class);
            assertThat(repository.findById(pendingId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
            assertThat(repository.findById(runningId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
            assertThat(repository.findById(succeededId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
        }
    }

    private ConfigurableApplicationContext start(String url, String ddl) {
        return new SpringApplicationBuilder(TerraformersBackendApplication.class, CapturingExecutorConfig.class)
                .profiles("test").web(WebApplicationType.SERVLET).run("--server.port=0",
                        "--spring.datasource.url=" + url, "--spring.jpa.hibernate.ddl-auto=" + ddl,
                        "--spring.flyway.enabled=false", "--terraformers.security.jwt.enabled=false",
                        "--terraformers.analysis.dispatch-enabled=true", "--terraformers.analysis.dispatch-poll-interval=1h");
    }

    private String datasourceUrl() {
        return "jdbc:h2:mem:restart-" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    private UserEntity owner(ConfigurableApplicationContext context) {
        UserEntity owner = new UserEntity();
        owner.setExternalIdentity("test", "owner-" + UUID.randomUUID());
        owner.setEmail("owner-" + UUID.randomUUID() + "@example.test");
        owner.setDisplayName("Restart Owner");
        return context.getBean(UserRepository.class).saveAndFlush(owner);
    }

    private OwnedProjectEntity project(ConfigurableApplicationContext context, UserEntity owner) {
        OwnedProjectEntity project = new OwnedProjectEntity();
        project.setOwner(owner); project.setName("Restart Project"); project.setVisibility(ProjectVisibility.PRIVATE);
        return context.getBean(OwnedProjectRepository.class).saveAndFlush(project);
    }

    private ProjectFileEntity source(ConfigurableApplicationContext context, UserEntity owner, OwnedProjectEntity project) {
        ProjectFileEntity source = new ProjectFileEntity();
        source.setProject(project); source.setUploadedBy(owner); source.setNodeType("FILE");
        source.setFileType("ARCHITECTURE_IMAGE"); source.setPath("source/restart.png");
        source.setS3Bucket("restart-source"); source.setS3Key("source/restart.png");
        source.setContentType("image/png"); source.setSizeBytes(1L);
        return context.getBean(ProjectFileRepository.class).saveAndFlush(source);
    }

    private AnalysisJobEntity job(AnalysisJobStatus status, String correlationId) {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(501L); entity.setSourceFileId(601L); entity.setSourceBucket("restart-bucket");
        entity.setSourceKey("source/restart.png"); entity.setCorrelationId(correlationId);
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA); entity.setStatus(status);
        return entity;
    }

    @TestConfiguration
    static class CapturingExecutorConfig {
        @Bean @Primary @Qualifier("analysisJobExecutor")
        CapturingExecutor capturingExecutor() { return new CapturingExecutor(); }
    }

    static class CapturingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();
        public synchronized void execute(Runnable command) { tasks.add(command); }
        synchronized List<Runnable> tasks() { return List.copyOf(tasks); }
        void runAll() {
            List<Runnable> copy;
            synchronized (this) { copy = List.copyOf(tasks); tasks.clear(); }
            copy.forEach(Runnable::run);
        }
    }
}
