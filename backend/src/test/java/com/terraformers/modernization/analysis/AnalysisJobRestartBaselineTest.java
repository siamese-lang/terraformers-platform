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
    void persistedPendingAndRunningJobsRemainStrandedAcrossApplicationRestart() {
        String databaseName = "terraformers-restart-" + UUID.randomUUID().toString().replace("-", "");
        String datasourceUrl = "jdbc:h2:mem:" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        String pendingJobId;
        String runningJobId;

        try (ConfigurableApplicationContext first = startApplication(datasourceUrl, "create")) {
            AnalysisJobRepository repository = first.getBean(AnalysisJobRepository.class);

            AnalysisJobEntity pending = repository.saveAndFlush(job(AnalysisJobStatus.PENDING, "restart-pending"));
            AnalysisJobEntity running = repository.saveAndFlush(job(AnalysisJobStatus.RUNNING, "restart-running"));

            pendingJobId = pending.getId();
            runningJobId = running.getId();
        }

        try (ConfigurableApplicationContext restarted = startApplication(datasourceUrl, "none")) {
            AnalysisJobRepository repository = restarted.getBean(AnalysisJobRepository.class);

            assertThat(repository.findById(pendingJobId))
                    .get()
                    .extracting(AnalysisJobEntity::getStatus)
                    .isEqualTo(AnalysisJobStatus.PENDING);
            assertThat(repository.findById(runningJobId))
                    .get()
                    .extracting(AnalysisJobEntity::getStatus)
                    .isEqualTo(AnalysisJobStatus.RUNNING);
        }
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
