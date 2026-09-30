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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

class AnalysisJobRestartBaselineTest {

    private static final Instant START = Instant.parse("2026-09-28T00:00:00Z");
    private static final MutableClock TEST_CLOCK = new MutableClock(START);
    private static final AtomicInteger PROVIDER_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger TIMEOUTS_REMAINING = new AtomicInteger();

    @BeforeEach
    void resetClock() {
        TEST_CLOCK.set(START);
        PROVIDER_INVOCATIONS.set(0);
        TIMEOUTS_REMAINING.set(0);
    }

    @Test
    void durableRetryWaitsUntilDueThenSucceedsOnSecondClaim() {
        TIMEOUTS_REMAINING.set(1);
        try (ConfigurableApplicationContext context = start(datasourceUrl(), "create")) {
            UserEntity owner = owner(context);
            OwnedProjectEntity project = project(context, owner);
            ProjectFileEntity source = source(context, owner, project);
            String jobId = context.getBean(AnalysisJobService.class).create(
                    new AnalysisJobRequest(project.getProjectId(), source.getFileId(), "retry-success"), owner).id();
            CapturingExecutor executor = context.getBean(CapturingExecutor.class);
            AnalysisJobDispatcher dispatcher = context.getBean(AnalysisJobDispatcher.class);

            executor.runAll();
            AnalysisJobEntity waiting = context.getBean(AnalysisJobRepository.class).findById(jobId).orElseThrow();
            assertThat(waiting.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
            assertThat(waiting.getAttemptCount()).isEqualTo(1);
            assertThat(waiting.getClaimGeneration()).isEqualTo(1);
            assertThat(waiting.getNextAttemptAt()).isEqualTo(START.plusSeconds(10));
            assertThat(waiting.getLeaseExpiresAt()).isNull();

            TEST_CLOCK.set(START.plusSeconds(9));
            dispatcher.dispatchEligible();
            assertThat(executor.tasks()).isEmpty();

            TEST_CLOCK.set(START.plusSeconds(10));
            dispatcher.dispatchEligible();
            assertThat(executor.tasks()).hasSize(1);
            executor.runAll();
            AnalysisJobEntity succeeded = context.getBean(AnalysisJobRepository.class).findById(jobId).orElseThrow();
            assertThat(succeeded.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
            assertThat(succeeded.getAttemptCount()).isEqualTo(2);
            assertThat(succeeded.getClaimGeneration()).isEqualTo(2);
            assertThat(PROVIDER_INVOCATIONS).hasValue(2);
        }
    }

    @Test
    void durableRetryExhaustionFailsThirdAttemptAndNeverExecutesFourth() {
        TIMEOUTS_REMAINING.set(Integer.MAX_VALUE);
        try (ConfigurableApplicationContext context = start(datasourceUrl(), "create")) {
            UserEntity owner = owner(context);
            OwnedProjectEntity project = project(context, owner);
            ProjectFileEntity source = source(context, owner, project);
            String jobId = context.getBean(AnalysisJobService.class).create(
                    new AnalysisJobRequest(project.getProjectId(), source.getFileId(), "retry-exhausted"), owner).id();
            CapturingExecutor executor = context.getBean(CapturingExecutor.class);
            AnalysisJobDispatcher dispatcher = context.getBean(AnalysisJobDispatcher.class);

            executor.runAll();
            TEST_CLOCK.set(START.plusSeconds(10));
            dispatcher.dispatchEligible();
            executor.runAll();
            TEST_CLOCK.set(START.plusSeconds(20));
            dispatcher.dispatchEligible();
            executor.runAll();

            AnalysisJobEntity failed = context.getBean(AnalysisJobRepository.class).findById(jobId).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(failed.getAttemptCount()).isEqualTo(3);
            assertThat(failed.getClaimGeneration()).isEqualTo(3);
            assertThat(failed.getNextAttemptAt()).isNull();
            assertThat(failed.getLeaseExpiresAt()).isNull();
            assertThat(failed.getFailureReason()).isEqualTo(AnalysisJobRunner.TIMEOUT_FAILURE_REASON);

            TEST_CLOCK.set(START.plusSeconds(40));
            dispatcher.dispatchEligible();
            executor.runAll();
            assertThat(PROVIDER_INVOCATIONS).hasValue(3);
        }
    }

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
    void applicationStartsWithTwoSecondDispatchDuration() {
        try (ConfigurableApplicationContext context = start(datasourceUrl(), "create", "2s")) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getBean(AnalysisRuntimeProperties.class).getDispatchPollInterval())
                    .isEqualTo(java.time.Duration.ofSeconds(2));
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

    @Test
    void claimedRunningJobIsNotStolenBeforeLeaseExpiryAndIsReclaimedAfterExpiry() {
        String datasourceUrl = datasourceUrl();
        String jobId;
        Instant firstLease = START.plusSeconds(60);
        try (ConfigurableApplicationContext first = start(datasourceUrl, "create")) {
            UserEntity owner = owner(first);
            OwnedProjectEntity project = project(first, owner);
            ProjectFileEntity source = source(first, owner, project);
            AnalysisJobEntity pending = jobForSource(project, source, "claimed-process-loss");
            jobId = first.getBean(AnalysisJobRepository.class).saveAndFlush(pending).getId();
            assertThat(first.getBean(AnalysisJobStateService.class)
                    .claimEligible(jobId, START, firstLease)).isPresent();
        }

        try (ConfigurableApplicationContext restarted = start(datasourceUrl, "none")) {
            AnalysisJobRepository repository = restarted.getBean(AnalysisJobRepository.class);
            AnalysisJobDispatcher dispatcher = restarted.getBean(AnalysisJobDispatcher.class);
            CapturingExecutor executor = restarted.getBean(CapturingExecutor.class);

            dispatcher.dispatchEligible();
            assertThat(executor.tasks()).isEmpty();
            AnalysisJobEntity active = repository.findById(jobId).orElseThrow();
            assertThat(active.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
            assertThat(active.getAttemptCount()).isEqualTo(1);
            assertThat(active.getClaimGeneration()).isEqualTo(1);

            TEST_CLOCK.set(firstLease.plusSeconds(1));
            dispatcher.dispatchEligible();
            assertThat(executor.tasks()).hasSize(1);
            executor.runAll();

            AnalysisJobEntity recovered = repository.findById(jobId).orElseThrow();
            assertThat(recovered.getId()).isEqualTo(jobId);
            assertThat(recovered.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
            assertThat(recovered.getAttemptCount()).isEqualTo(2);
            assertThat(recovered.getClaimGeneration()).isEqualTo(2);
        }
    }

    @Test
    void legacyRunningWithoutLeaseIsRecoveredThroughDispatcherAndRunner() {
        String datasourceUrl = datasourceUrl();
        try (ConfigurableApplicationContext context = start(datasourceUrl, "create")) {
            UserEntity owner = owner(context);
            OwnedProjectEntity project = project(context, owner);
            ProjectFileEntity source = source(context, owner, project);
            AnalysisJobEntity legacy = jobForSource(project, source, "legacy-null-lease");
            legacy.setStatus(AnalysisJobStatus.RUNNING);
            String jobId = context.getBean(AnalysisJobRepository.class).saveAndFlush(legacy).getId();

            AnalysisJobDispatcher dispatcher = context.getBean(AnalysisJobDispatcher.class);
            CapturingExecutor executor = context.getBean(CapturingExecutor.class);
            dispatcher.dispatchEligible();
            assertThat(executor.tasks()).hasSize(1);
            executor.runAll();

            AnalysisJobEntity recovered = context.getBean(AnalysisJobRepository.class).findById(jobId).orElseThrow();
            assertThat(recovered.getId()).isEqualTo(jobId);
            assertThat(recovered.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
            assertThat(recovered.getAttemptCount()).isEqualTo(1);
            assertThat(recovered.getClaimGeneration()).isEqualTo(1);
        }
    }

    private ConfigurableApplicationContext start(String url, String ddl) {
        return start(url, ddl, "1h");
    }

    private ConfigurableApplicationContext start(String url, String ddl, String pollInterval) {
        return new SpringApplicationBuilder(
                TerraformersBackendApplication.class,
                CapturingExecutorConfig.class,
                PassThroughTerraformExecutableValidatorTestConfig.class
        )
                .profiles("test").web(WebApplicationType.SERVLET).run("--server.port=0",
                        "--spring.datasource.url=" + url, "--spring.jpa.hibernate.ddl-auto=" + ddl,
                        "--spring.flyway.enabled=false", "--terraformers.security.jwt.enabled=false",
                        "--spring.main.allow-bean-definition-overriding=true",
                        "--terraformers.analysis.dispatch-enabled=true",
                        "--terraformers.analysis.dispatch-poll-interval=" + pollInterval);
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

    private AnalysisJobEntity jobForSource(OwnedProjectEntity project, ProjectFileEntity source,
            String correlationId) {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(project.getProjectId());
        entity.setSourceFileId(source.getFileId());
        entity.setSourceBucket(source.getS3Bucket());
        entity.setSourceKey(source.getS3Key());
        entity.setCorrelationId(correlationId);
        entity.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        entity.setStatus(AnalysisJobStatus.PENDING);
        return entity;
    }

    @TestConfiguration
    static class CapturingExecutorConfig {
        @Bean @Primary @Qualifier("analysisJobExecutor")
        CapturingExecutor capturingExecutor() { return new CapturingExecutor(); }

        @Bean
        @Primary
        Clock testClock() { return TEST_CLOCK; }

        @Bean(name = "selectedAnalysisProvider") @Primary
        AnalysisProvider selectedAnalysisProvider() {
            return context -> {
                PROVIDER_INVOCATIONS.incrementAndGet();
                if (TIMEOUTS_REMAINING.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0) {
                    throw new AnalysisProviderTimeoutException(new java.net.SocketTimeoutException("test timeout"));
                }
                String terraform = """
                        resource "null_resource" "generated" {
                          triggers = {
                            source = "retry-test"
                          }
                        }
                        """;
                return new AnalysisResult("test", terraform, "summary",
                        List.of(), List.of(), List.of(), List.of());
            };
        }
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

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) { this.current = new AtomicReference<>(initial); }
        void set(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }
}
