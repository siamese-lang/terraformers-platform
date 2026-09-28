package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.TerraformersBackendApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class AnalysisJobRestartBaselineTest {

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
}
