package com.terraformers.modernization.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.terraformers.modernization.analysis.AnalysisJobEntity;
import com.terraformers.modernization.analysis.AnalysisJobRepository;
import com.terraformers.modernization.analysis.AnalysisJobStatus;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.analysis.AnalysisResultCleanupStatus;
import com.terraformers.modernization.collaboration.BoardEntity;
import com.terraformers.modernization.collaboration.BoardRepository;
import com.terraformers.modernization.collaboration.CommentEntity;
import com.terraformers.modernization.collaboration.CommentRepository;
import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.identity.UserRepository;
import com.terraformers.modernization.identity.AuthenticatedUserService;
import com.terraformers.modernization.identity.CognitoJwtExternalIdentityMapper;
import com.terraformers.modernization.identity.JwtExternalIdentityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import jakarta.persistence.EntityManagerFactory;
import com.terraformers.modernization.project.ProjectVisibility;
import com.terraformers.modernization.projectcore.OwnedProjectEntity;
import com.terraformers.modernization.projectcore.OwnedProjectRepository;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.projectcore.ProjectFileRepository;
import com.terraformers.modernization.projectcore.ProjectStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@SpringBootTest(properties = "terraformers.analysis.dispatch-enabled=false")
@ActiveProfiles("prod")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "^jdbc:.*")
@Transactional
@Import(MariaDbRepositorySmokeTest.IdentityObservationConfiguration.class)
class MariaDbRepositorySmokeTest {

    private static final long BARRIER_TIMEOUT_SECONDS = 10;
    private static final long CLAIM_TIMEOUT_SECONDS = 20;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityRepositoryObservation identityObservation;

    @TestConfiguration(proxyBeanMethods = false)
    static class IdentityObservationConfiguration {
        @Bean
        static IdentityRepositoryObservation identityRepositoryObservation() {
            return new IdentityRepositoryObservation();
        }
    }

    // Observe and coordinate only; every invocation proceeds through the real repository proxy.
    static class IdentityRepositoryObservation implements BeanPostProcessor {
        private volatile MethodInterceptor observer;

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!"userRepository".equals(beanName)) return bean;
            ProxyFactory proxy = new ProxyFactory(bean);
            proxy.addAdvice((MethodInterceptor) invocation -> {
                MethodInterceptor active = observer;
                return active == null ? invocation.proceed() : active.invoke(invocation);
            });
            return proxy.getProxy();
        }
    }

    @Autowired
    private AuthenticatedUserService authenticatedUserService;

    @Autowired
    private JwtExternalIdentityMapper identityMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private OwnedProjectRepository projectRepository;

    @Autowired
    private ProjectFileRepository projectFileRepository;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstExternalIdentityRequestsConvergeAgainstMariaDb() throws Exception {
        assertThat(AopUtils.isAopProxy(authenticatedUserService)).isTrue();
        assertThat(identityMapper).isInstanceOf(CognitoJwtExternalIdentityMapper.class);
        String subject = "mariadb-first-" + UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("local-database-test").header("alg", "RS256")
                .subject(subject).claim("cognito:username", "First user")
                .claim("role", "ADMIN").claim("status", "INACTIVE").build();
        CyclicBarrier firstLookups = new CyclicBarrier(2);
        Map<String, AtomicInteger> lookups = new ConcurrentHashMap<>();
        ConcurrentLinkedQueue<Map<String, Object>> events = new ConcurrentLinkedQueue<>();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("databaseVersion", jdbcTemplate.queryForObject("SELECT VERSION()", String.class));
        evidence.put("isolation", jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class));
        evidence.put("provider", "cognito");
        evidence.put("subject", subject);
        evidence.put("springProxiedService", true);
        evidence.put("callerCount", 2);
        evidence.put("events", events);

        identityObservation.observer = invocation -> {
            String operation = invocation.getMethod().getName();
            Object[] arguments = invocation.getArguments();
            boolean lookup = "findByExternalIdentityProviderAndExternalIdentitySubject".equals(operation)
                    && "cognito".equals(arguments[0]) && subject.equals(arguments[1]);
            boolean save = "save".equals(operation) && arguments[0] instanceof UserEntity user
                    && subject.equals(user.getExternalIdentitySubject());
            if (!lookup && !save) return invocation.proceed();
            String caller = Thread.currentThread().getName();
            int number = lookup ? lookups.computeIfAbsent(caller, ignored -> new AtomicInteger()).incrementAndGet() : 0;
            Map<String, Object> event = identityEvent(lookup ? "lookup" : "save", number);
            events.add(event);
            try {
                Object result = invocation.proceed();
                if (lookup) {
                    boolean absent = ((java.util.Optional<?>) result).isEmpty();
                    event.put("absent", absent);
                    if (number == 1 && absent) firstLookups.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } else {
                    event.put("returned", true);
                }
                return result;
            } catch (Throwable failure) {
                event.put("failure", identityFailure(failure));
                if (save) {
                    event.put("returned", false);
                    event.put("rollbackOnlyAfterFailure", TransactionAspectSupport.currentTransactionStatus().isRollbackOnly());
                    var persistenceContext = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
                    event.put("persistenceTransactionRollbackOnlyAfterFailure", persistenceContext.getTransaction().getRollbackOnly());
                }
                throw failure;
            }
        };

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> first = callers.submit(() -> firstIdentityOutcome(jwt));
            Future<Map<String, Object>> second = callers.submit(() -> firstIdentityOutcome(jwt));
            List<Map<String, Object>> outcomes = List.of(
                    first.get(CLAIM_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(CLAIM_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            evidence.put("outcomes", outcomes);
            List<Map<String, Object>> durable = jdbcTemplate.queryForList(
                    "SELECT user_id, external_identity_provider, external_identity_subject, role, status, display_name "
                            + "FROM users WHERE external_identity_provider = ? AND external_identity_subject = ?",
                    "cognito", subject);
            evidence.put("durableRows", durable);
            long absent = events.stream().filter(e -> "lookup".equals(e.get("operation"))
                    && Integer.valueOf(1).equals(e.get("lookupNumber")) && Boolean.TRUE.equals(e.get("absent"))).count();
            evidence.put("initialAbsentLookups", absent);
            assertThat(absent).isEqualTo(2);
            assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).doesNotContainKey("failure"));
            assertThat(outcomes.get(0).get("userId")).isEqualTo(outcomes.get(1).get("userId"));
            assertThat(outcomes).allSatisfy(outcome -> {
                assertThat(outcome.get("role")).isEqualTo("USER");
                assertThat(outcome.get("status")).isEqualTo("ACTIVE");
            });
            assertThat(durable).hasSize(1);
            assertThat(((Number) durable.get(0).get("user_id")).longValue())
                    .isEqualTo(((Number) outcomes.get(0).get("userId")).longValue());
            assertThat(durable.get(0)).containsEntry("external_identity_provider", "cognito")
                    .containsEntry("external_identity_subject", subject).containsEntry("role", "USER")
                    .containsEntry("status", "ACTIVE");
            assertThat(events.stream().filter(e -> "save".equals(e.get("operation"))).toList()).hasSize(2);
            assertThat(events.stream().filter(e -> "lookup".equals(e.get("operation"))
                    && Integer.valueOf(2).equals(e.get("lookupNumber"))).toList())
                    .singleElement().satisfies(event -> assertThat(event).containsEntry("absent", false)
                            .doesNotContainKey("failure"));
        } finally {
            callers.shutdownNow();
            boolean stopped = callers.awaitTermination(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            identityObservation.observer = null;
            jdbcTemplate.update("DELETE FROM users WHERE external_identity_provider = ? AND external_identity_subject = ?",
                    "cognito", subject);
            evidence.put("remainingTestRows", jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE external_identity_provider = ? AND external_identity_subject = ?",
                    Integer.class, "cognito", subject));
            evidence.put("callersStopped", stopped);
            System.out.println("PT6R1_IDENTITY_RACE " + objectMapper.writeValueAsString(evidence));
            assertThat(stopped).isTrue();
            assertThat(evidence.get("remainingTestRows")).isEqualTo(0);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void externalIdentityOwnershipAndDisplayNamesRemainIsolatedAgainstMariaDb() throws Exception {
        String prefix = "mariadb-identity-control-" + UUID.randomUUID();
        String firstSubject = prefix + "-first";
        String secondSubject = prefix + "-second";
        String conflictingSubject = prefix + "-conflict";
        String email = prefix + "@example.test";
        Jwt firstJwt = identityJwt(firstSubject, email, null);
        Jwt secondJwt = identityJwt(secondSubject, null, null);
        Map<String, Object> evidence = new LinkedHashMap<>();
        try {
            UserEntity first = authenticatedUserService.getOrCreate(firstJwt);
            UserEntity second = authenticatedUserService.getOrCreate(secondJwt);
            assertThat(first.getUserId()).isNotEqualTo(second.getUserId());
            assertThatThrownBy(() -> authenticatedUserService.getOrCreate(identityJwt(conflictingSubject, email, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("authenticated email is already linked to another external identity");
            authenticatedUserService.updateCurrentDisplayName(firstJwt, "Custom nickname");
            assertThat(authenticatedUserService.getOrCreate(firstJwt).getDisplayName()).isEqualTo("Custom nickname");
            assertThat(authenticatedUserService.getOrCreate(identityJwt(firstSubject, email, "Explicit name"))
                    .getDisplayName()).isEqualTo("Explicit name");
            assertThat(first.getRole().name()).isEqualTo("USER");
            assertThat(second.getStatus().name()).isEqualTo("ACTIVE");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT user_id, external_identity_subject, email, role, status, display_name FROM users "
                            + "WHERE external_identity_provider = ? AND external_identity_subject IN (?, ?, ?)",
                    "cognito", firstSubject, secondSubject, conflictingSubject);
            assertThat(rows).hasSize(2);
            assertThat(rows).allSatisfy(row -> assertThat(row).containsEntry("role", "USER").containsEntry("status", "ACTIVE"));
            evidence.put("durableRows", rows);
            evidence.put("distinctIdentities", true);
            evidence.put("emailConflictRejected", true);
            evidence.put("fallbackPreservesCustomDisplayName", true);
            evidence.put("explicitDisplayNameUpdates", true);
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE external_identity_provider = ? AND external_identity_subject IN (?, ?, ?)",
                    "cognito", firstSubject, secondSubject, conflictingSubject);
            int remaining = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE external_identity_provider = ? AND external_identity_subject IN (?, ?, ?)",
                    Integer.class, "cognito", firstSubject, secondSubject, conflictingSubject);
            evidence.put("remainingTestRows", remaining);
            System.out.println("PT6R1_IDENTITY_CONTROLS " + objectMapper.writeValueAsString(evidence));
            assertThat(remaining).isZero();
        }
    }

    private Jwt identityJwt(String subject, String email, String explicitName) {
        var builder = Jwt.withTokenValue("local-database-control").header("alg", "RS256")
                .subject(subject).claim("cognito:username", "Provider fallback")
                .claim("role", "ADMIN").claim("status", "DISABLED");
        if (email != null) builder.claim("email", email);
        if (explicitName != null) builder.claim("name", explicitName);
        return builder.build();
    }

    private Map<String, Object> firstIdentityOutcome(Jwt jwt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caller", Thread.currentThread().getName());
        try {
            UserEntity user = authenticatedUserService.getOrCreate(jwt);
            result.put("userId", user.getUserId());
            result.put("role", user.getRole().name());
            result.put("status", user.getStatus().name());
        } catch (Throwable failure) {
            result.put("failure", identityFailure(failure));
        }
        return result;
    }

    private Map<String, Object> identityEvent(String operation, int lookupNumber) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("caller", Thread.currentThread().getName());
        event.put("operation", operation);
        event.put("lookupNumber", lookupNumber);
        event.put("transactionActive", TransactionSynchronizationManager.isActualTransactionActive());
        return event;
    }

    private List<Map<String, Object>> identityFailure(Throwable failure) {
        java.util.ArrayList<Map<String, Object>> chain = new java.util.ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("class", cause.getClass().getName());
            entry.put("message", cause.getMessage());
            if (cause instanceof SQLException sql) {
                entry.put("sqlState", sql.getSQLState());
                entry.put("errorCode", sql.getErrorCode());
            }
            chain.add(entry);
        }
        return chain;
    }

    @Test
    void durableEligibilityDiscoveryExecutesAgainstMariaDb() {
        Instant now = Instant.parse("2026-09-28T00:00:00Z");
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = new UserEntity();
        owner.setExternalIdentity("cognito", "mariadb-eligibility-" + suffix);
        owner.setEmail("mariadb-eligibility-" + suffix + "@example.com");
        owner.setDisplayName("MariaDB Eligibility User");
        owner = userRepository.saveAndFlush(owner);
        OwnedProjectEntity project = new OwnedProjectEntity();
        project.setOwner(owner);
        project.setName("MariaDB Eligibility");
        project.setVisibility(ProjectVisibility.PRIVATE);
        project.setStatus(ProjectStatus.ACTIVE);
        project = projectRepository.saveAndFlush(project);
        ProjectFileEntity source = new ProjectFileEntity();
        source.setProject(project);
        source.setUploadedBy(owner);
        source.setNodeType("FILE");
        source.setFileType("ARCHITECTURE_IMAGE");
        source.setPath("source/eligibility.png");
        source.setS3Bucket("mariadb-eligibility");
        source.setS3Key("source/eligibility.png");
        source.setContentType("image/png");
        source.setSizeBytes(1L);
        source = projectFileRepository.saveAndFlush(source);

        AnalysisJobEntity due = saveEligibilityJob(project, source, "mariadb-due", AnalysisJobStatus.PENDING);
        AnalysisJobEntity future = saveEligibilityJob(project, source, "mariadb-future", AnalysisJobStatus.PENDING);
        future.setNextAttemptAt(now.plusSeconds(30));
        analysisJobRepository.saveAndFlush(future);
        AnalysisJobEntity expired = saveEligibilityJob(project, source, "mariadb-expired", AnalysisJobStatus.PENDING);
        analysisJobRepository.claimEligible(expired.getId(), AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING, now.minusSeconds(120), now.minusSeconds(60));
        AnalysisJobEntity active = saveEligibilityJob(project, source, "mariadb-active", AnalysisJobStatus.PENDING);
        analysisJobRepository.claimEligible(active.getId(), AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING, now.minusSeconds(1), now.plusSeconds(60));
        AnalysisJobEntity legacy = saveEligibilityJob(project, source, "mariadb-legacy", AnalysisJobStatus.PENDING);
        analysisJobRepository.claimPending(legacy.getId(), AnalysisJobStatus.PENDING,
                AnalysisJobStatus.RUNNING, now.minusSeconds(60));
        AnalysisJobEntity succeeded = saveEligibilityJob(project, source, "mariadb-succeeded", AnalysisJobStatus.SUCCEEDED);
        AnalysisJobEntity failed = saveEligibilityJob(project, source, "mariadb-failed", AnalysisJobStatus.FAILED);

        List<String> eligible = analysisJobRepository.findEligibleJobIds(
                AnalysisJobStatus.PENDING, AnalysisJobStatus.RUNNING, now, PageRequest.of(0, 10));

        assertThat(eligible).contains(due.getId(), expired.getId(), legacy.getId());
        assertThat(eligible).doesNotContain(future.getId(), active.getId(), succeeded.getId(), failed.getId());
    }

    private AnalysisJobEntity saveEligibilityJob(OwnedProjectEntity project, ProjectFileEntity source,
            String correlationId, AnalysisJobStatus status) {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(project.getProjectId());
        job.setSourceFileId(source.getFileId());
        job.setSourceBucket(source.getS3Bucket());
        job.setSourceKey(source.getS3Key());
        job.setCorrelationId(correlationId);
        job.setStatus(status);
        job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        return analysisJobRepository.saveAndFlush(job);
    }

    private int claimInIndependentTransaction(String jobId, CyclicBarrier contendersReady) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                contendersReady.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("concurrent claim barrier was interrupted", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new IllegalStateException("concurrent claim barrier failed", exception);
            }
            return analysisJobRepository.claimPending(
                    jobId,
                    AnalysisJobStatus.PENDING,
                    AnalysisJobStatus.RUNNING,
                    Instant.now()
            );
        });
    }

    private int awaitClaim(Future<Integer> claim) {
        try {
            return claim.get(CLAIM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent claim wait was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("concurrent claim did not complete within the test bound", exception);
        }
    }

    private int durableClaimInIndependentTransaction(
            String jobId, Instant now, Instant leaseExpiry, CyclicBarrier contendersReady
    ) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                contendersReady.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("durable claim barrier was interrupted", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new IllegalStateException("durable claim barrier failed", exception);
            }
            return analysisJobRepository.claimEligible(jobId, AnalysisJobStatus.PENDING,
                    AnalysisJobStatus.RUNNING, now, leaseExpiry);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void canonicalRepositoryQueriesAndConcurrentClaimExecuteAgainstMariaDb() {
        String suffix = UUID.randomUUID().toString();
        UserEntity owner = null;
        OwnedProjectEntity project = null;
        ProjectFileEntity sourceFile = null;
        ProjectFileEntity deletedDraft = null;
        ProjectFileEntity resultFile = null;
        AnalysisJobEntity job = null;
        AnalysisJobEntity claimJob = null;
        AnalysisJobEntity durableInitialJob = null;
        AnalysisJobEntity durableReclaimJob = null;
        BoardEntity board = null;
        CommentEntity comment = null;

        try {
            owner = new UserEntity();
            owner.setExternalIdentity("cognito", "mariadb-smoke-" + suffix);
            owner.setEmail("mariadb-smoke-" + suffix + "@example.com");
            owner.setDisplayName("MariaDB Smoke User");
            owner = userRepository.saveAndFlush(owner);

            project = new OwnedProjectEntity();
            project.setOwner(owner);
            project.setName("MariaDB Repository Smoke");
            project.setDescription("Exercises canonical repository queries against MariaDB.");
            project.setVisibility(ProjectVisibility.PUBLIC);
            project.setStatus(ProjectStatus.ACTIVE);
            project = projectRepository.saveAndFlush(project);

            sourceFile = new ProjectFileEntity();
            sourceFile.setProject(project);
            sourceFile.setUploadedBy(owner);
            sourceFile.setNodeType("FILE");
            sourceFile.setFileType("ARCHITECTURE_IMAGE");
            sourceFile.setPath("source/architecture.png");
            sourceFile.setOriginalFilename("architecture.png");
            sourceFile.setS3Bucket("validation-bucket");
            sourceFile.setS3Key("repository-smoke/architecture.png");
            sourceFile.setStorageProvider("metadata-only");
            sourceFile.setBinaryPersisted(false);
            sourceFile.setContentType("image/png");
            sourceFile.setSizeBytes(16L);
            sourceFile.setSortOrder(0);
            sourceFile = projectFileRepository.saveAndFlush(sourceFile);

            deletedDraft = new ProjectFileEntity();
            deletedDraft.setProject(project);
            deletedDraft.setUploadedBy(owner);
            deletedDraft.setNodeType("FILE");
            deletedDraft.setFileType("GENERATED_TERRAFORM");
            deletedDraft.setPath("terraform/main-old.tf");
            deletedDraft.setInlineContent("terraform {}");
            deletedDraft.setContentType("text/plain; charset=utf-8");
            deletedDraft.setSizeBytes(12L);
            deletedDraft.setChecksum("deleted-checksum");
            deletedDraft.setSortOrder(1);
            deletedDraft.setDeletedAt(Instant.now());
            deletedDraft = projectFileRepository.saveAndFlush(deletedDraft);

            resultFile = new ProjectFileEntity();
            resultFile.setProject(project);
            resultFile.setUploadedBy(owner);
            resultFile.setNodeType("FILE");
            resultFile.setFileType("GENERATED_TERRAFORM");
            resultFile.setPath("terraform/main.tf");
            resultFile.setS3Bucket("validation-bucket");
            resultFile.setS3Key("repository-smoke/main.tf");
            resultFile.setStorageProvider("metadata-only");
            resultFile.setBinaryPersisted(false);
            resultFile.setContentType("text/plain; charset=utf-8");
            resultFile.setInlineContent("terraform { required_version = \">= 1.6.0\" }");
            resultFile.setSizeBytes(41L);
            resultFile.setChecksum("active-checksum");
            resultFile.setSortOrder(2);
            resultFile = projectFileRepository.saveAndFlush(resultFile);

            job = new AnalysisJobEntity();
            job.setProjectId(project.getProjectId());
            job.setSourceFileId(sourceFile.getFileId());
            job.setResultFileId(resultFile.getFileId());
            job.setSourceBucket(sourceFile.getS3Bucket());
            job.setSourceKey(sourceFile.getS3Key());
            job.setCorrelationId("repository-smoke");
            job.setStatus(AnalysisJobStatus.SUCCEEDED);
            job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
            job.setProvider("repository-smoke-provider");
            job.setResultObjectKey(resultFile.getS3Key());
            job.setResultPreview("terraform {}");
            job = analysisJobRepository.saveAndFlush(job);

            claimJob = new AnalysisJobEntity();
            claimJob.setProjectId(project.getProjectId());
            claimJob.setSourceFileId(sourceFile.getFileId());
            claimJob.setSourceBucket(sourceFile.getS3Bucket());
            claimJob.setSourceKey(sourceFile.getS3Key());
            claimJob.setCorrelationId("repository-smoke-claim");
            claimJob.setStatus(AnalysisJobStatus.PENDING);
            claimJob.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
            claimJob = analysisJobRepository.saveAndFlush(claimJob);
            String claimJobId = claimJob.getId();

            CyclicBarrier contendersReady = new CyclicBarrier(2);
            ExecutorService contenders = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = contenders.submit(
                        () -> claimInIndependentTransaction(claimJobId, contendersReady)
                );
                Future<Integer> second = contenders.submit(
                        () -> claimInIndependentTransaction(claimJobId, contendersReady)
                );
                assertThat(List.of(
                        awaitClaim(first),
                        awaitClaim(second)
                )).containsExactlyInAnyOrder(0, 1);
            } finally {
                contenders.shutdownNow();
            }
            assertThat(analysisJobRepository.findById(claimJobId))
                    .get()
                    .extracting(AnalysisJobEntity::getStatus)
                    .isEqualTo(AnalysisJobStatus.RUNNING);
            analysisJobRepository.deleteById(claimJobId);
            analysisJobRepository.flush();

            AnalysisJobEntity latestJob = analysisJobRepository
                    .findFirstByProjectIdOrderByCreatedAtDesc(project.getProjectId())
                    .orElseThrow();
            assertThat(latestJob.getId()).isEqualTo(job.getId());
            assertThat(latestJob.getSourceFileId()).isEqualTo(sourceFile.getFileId());
            assertThat(latestJob.getResultFileId()).isEqualTo(resultFile.getFileId());

            Instant durableNow = Instant.parse("2026-09-28T00:00:00Z");
            Instant durableLease = Instant.parse("2026-09-28T00:05:00Z");
            durableInitialJob = newClaimJob(project, sourceFile, "repository-smoke-durable-initial");
            durableInitialJob = analysisJobRepository.saveAndFlush(durableInitialJob);
            assertConcurrentDurableClaim(durableInitialJob.getId(), durableNow, durableLease);
            AnalysisJobEntity claimed = analysisJobRepository.findById(durableInitialJob.getId()).orElseThrow();
            assertThat(claimed.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
            assertThat(claimed.getAttemptCount()).isEqualTo(1);
            assertThat(claimed.getClaimGeneration()).isEqualTo(1);
            assertThat(recordIntentInIndependentTransaction(claimed.getId(), 1, durableNow,
                    "result-bucket", "result-key")).isEqualTo(1);
            assertThat(recordIntentInIndependentTransaction(claimed.getId(), 1, durableNow,
                    "result-bucket", "result-key")).isEqualTo(1);
            assertThat(recordIntentInIndependentTransaction(claimed.getId(), 1, durableNow,
                    "other-bucket", "other-key")).isZero();
            assertThat(markCleanupCompletedInIndependentTransaction(claimed.getId(), 2, durableNow,
                    "result-bucket", "result-key")).isZero();
            assertThat(markFailedInIndependentTransaction(claimed.getId(), 1, durableNow,
                    "cleanup required")).isEqualTo(1);
            assertThat(analysisJobRepository.findPendingCleanupJobIds(AnalysisJobStatus.FAILED,
                    AnalysisResultCleanupStatus.PENDING, PageRequest.of(0, 1)))
                    .containsExactly(claimed.getId());

            durableReclaimJob = newClaimJob(project, sourceFile, "repository-smoke-durable-reclaim");
            durableReclaimJob.setStatus(AnalysisJobStatus.RUNNING);
            durableReclaimJob.setAttemptCount(1);
            durableReclaimJob.setClaimGeneration(1);
            durableReclaimJob.setLeaseExpiresAt(durableNow.minusSeconds(1));
            durableReclaimJob = analysisJobRepository.saveAndFlush(durableReclaimJob);
            assertConcurrentDurableClaim(durableReclaimJob.getId(), durableNow, durableLease);
            AnalysisJobEntity reclaimed = analysisJobRepository.findById(durableReclaimJob.getId()).orElseThrow();
            assertThat(reclaimed.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
            assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
            assertThat(reclaimed.getClaimGeneration()).isEqualTo(2);

            board = new BoardEntity();
            board.setProject(project);
            board.setAuthor(owner);
            board.setTitle(BoardEntity.PUBLIC_DISCUSSION_TITLE);
            board.setContent("Public project discussion");
            board.setCategory(BoardEntity.PUBLIC_DISCUSSION_CATEGORY);
            board = boardRepository.saveAndFlush(board);

            comment = new CommentEntity();
            comment.setBoard(board);
            comment.setAuthor(owner);
            comment.setContent("Repository smoke comment");
            comment = commentRepository.saveAndFlush(comment);

            Long ownerId = owner.getUserId();
            Long projectId = project.getProjectId();
            Long sourceFileId = sourceFile.getFileId();
            Long resultFileId = resultFile.getFileId();
            Long boardId = board.getBoardId();
            Long commentId = comment.getCommentId();

            assertThat(userRepository.findByExternalIdentityProviderAndExternalIdentitySubject(
                    "cognito",
                    "mariadb-smoke-" + suffix
            ))
                    .get()
                    .extracting(UserEntity::getUserId)
                    .isEqualTo(ownerId);
            assertThat(projectRepository.findByOwner_UserIdAndDeletedAtIsNullOrderByCreatedAtDesc(ownerId))
                    .extracting(OwnedProjectEntity::getProjectId)
                    .containsExactly(projectId);
            assertThat(projectRepository.findByVisibilityAndDeletedAtIsNullOrderByCreatedAtDesc(ProjectVisibility.PUBLIC))
                    .extracting(OwnedProjectEntity::getProjectId)
                    .contains(projectId);
            assertThat(projectRepository.existsByProjectIdAndOwner_UserIdAndDeletedAtIsNull(projectId, ownerId)).isTrue();

            List<ProjectFileEntity> activeFiles = projectFileRepository
                    .findByProject_ProjectIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(projectId);
            assertThat(activeFiles)
                    .extracting(ProjectFileEntity::getPath)
                    .containsExactly("source/architecture.png", "terraform/main.tf");
            assertThat(activeFiles)
                    .extracting(ProjectFileEntity::getFileId)
                    .containsExactly(sourceFileId, resultFileId);

            assertThat(boardRepository.findFirstByProject_ProjectIdAndCategoryAndDeletedAtIsNullOrderByCreatedAtAsc(
                    projectId,
                    BoardEntity.PUBLIC_DISCUSSION_CATEGORY
            )).get().extracting(BoardEntity::getBoardId).isEqualTo(boardId);
            assertThat(commentRepository.findByBoard_BoardIdAndDeletedAtIsNullOrderByCreatedAtAsc(boardId))
                    .extracting(CommentEntity::getCommentId)
                    .containsExactly(commentId);
        } finally {
            cleanupCreatedRows(comment, board, durableReclaimJob, durableInitialJob, claimJob, job,
                    resultFile, deletedDraft, sourceFile, project, owner);
        }

        assertThat(commentRepository.existsById(comment.getCommentId())).isFalse();
        assertThat(boardRepository.existsById(board.getBoardId())).isFalse();
        assertThat(analysisJobRepository.existsById(claimJob.getId())).isFalse();
        assertThat(analysisJobRepository.existsById(durableInitialJob.getId())).isFalse();
        assertThat(analysisJobRepository.existsById(durableReclaimJob.getId())).isFalse();
        assertThat(analysisJobRepository.existsById(job.getId())).isFalse();
        assertThat(projectFileRepository.existsById(resultFile.getFileId())).isFalse();
        assertThat(projectFileRepository.existsById(deletedDraft.getFileId())).isFalse();
        assertThat(projectFileRepository.existsById(sourceFile.getFileId())).isFalse();
        assertThat(projectRepository.existsById(project.getProjectId())).isFalse();
        assertThat(userRepository.existsById(owner.getUserId())).isFalse();
    }

    private int recordIntentInIndependentTransaction(String jobId, long generation, Instant now,
            String bucket, String key) {
        return new TransactionTemplate(transactionManager).execute(status ->
                analysisJobRepository.recordResultObjectIntentOwned(jobId, AnalysisJobStatus.RUNNING,
                        generation, now, bucket, key, AnalysisResultCleanupStatus.PENDING));
    }

    private int markCleanupCompletedInIndependentTransaction(String jobId, long generation, Instant now,
            String bucket, String key) {
        return new TransactionTemplate(transactionManager).execute(status ->
                analysisJobRepository.markResultCleanupCompleted(jobId, generation, bucket, key,
                        AnalysisResultCleanupStatus.PENDING, AnalysisResultCleanupStatus.COMPLETED, now));
    }

    private int markFailedInIndependentTransaction(String jobId, long generation, Instant now,
            String failureReason) {
        return new TransactionTemplate(transactionManager).execute(status ->
                analysisJobRepository.markFailedOwned(jobId, AnalysisJobStatus.RUNNING,
                        AnalysisJobStatus.FAILED, generation, now, failureReason));
    }

    private void cleanupCreatedRows(
            CommentEntity comment,
            BoardEntity board,
            AnalysisJobEntity durableReclaimJob,
            AnalysisJobEntity durableInitialJob,
            AnalysisJobEntity claimJob,
            AnalysisJobEntity job,
            ProjectFileEntity resultFile,
            ProjectFileEntity deletedDraft,
            ProjectFileEntity sourceFile,
            OwnedProjectEntity project,
            UserEntity owner
    ) {
        RuntimeException failure = null;
        failure = cleanup("comment", comment, failure,
                () -> commentRepository.deleteById(comment.getCommentId()));
        failure = cleanup("board", board, failure,
                () -> boardRepository.deleteById(board.getBoardId()));
        failure = cleanup("durable reclaim job", durableReclaimJob, failure,
                () -> analysisJobRepository.deleteById(durableReclaimJob.getId()));
        failure = cleanup("durable initial job", durableInitialJob, failure,
                () -> analysisJobRepository.deleteById(durableInitialJob.getId()));
        failure = cleanup("concurrent claim job", claimJob, failure,
                () -> analysisJobRepository.deleteById(claimJob.getId()));
        failure = cleanup("analysis job", job, failure,
                () -> analysisJobRepository.deleteById(job.getId()));
        failure = cleanup("result project file", resultFile, failure,
                () -> projectFileRepository.deleteById(resultFile.getFileId()));
        failure = cleanup("deleted project file", deletedDraft, failure,
                () -> projectFileRepository.deleteById(deletedDraft.getFileId()));
        failure = cleanup("source project file", sourceFile, failure,
                () -> projectFileRepository.deleteById(sourceFile.getFileId()));
        failure = cleanup("project", project, failure,
                () -> projectRepository.deleteById(project.getProjectId()));
        failure = cleanup("user", owner, failure,
                () -> userRepository.deleteById(owner.getUserId()));
        if (failure != null) {
            throw failure;
        }
    }

    private AnalysisJobEntity newClaimJob(OwnedProjectEntity project, ProjectFileEntity sourceFile,
            String correlationId) {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(project.getProjectId());
        job.setSourceFileId(sourceFile.getFileId());
        job.setSourceBucket(sourceFile.getS3Bucket());
        job.setSourceKey(sourceFile.getS3Key());
        job.setCorrelationId(correlationId);
        job.setStatus(AnalysisJobStatus.PENDING);
        job.setAnalysisMode(AnalysisMode.INTEGRATED_JAVA);
        return job;
    }

    private void assertConcurrentDurableClaim(String jobId, Instant now, Instant leaseExpiry) {
        CyclicBarrier contendersReady = new CyclicBarrier(2);
        ExecutorService contenders = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = contenders.submit(
                    () -> durableClaimInIndependentTransaction(jobId, now, leaseExpiry, contendersReady));
            Future<Integer> second = contenders.submit(
                    () -> durableClaimInIndependentTransaction(jobId, now, leaseExpiry, contendersReady));
            assertThat(List.of(awaitClaim(first), awaitClaim(second))).containsExactlyInAnyOrder(0, 1);
        } finally {
            contenders.shutdownNow();
        }
    }

    private RuntimeException cleanup(
            String fixture,
            Object entity,
            RuntimeException priorFailure,
            Runnable delete
    ) {
        if (entity == null) {
            return priorFailure;
        }
        try {
            delete.run();
        } catch (RuntimeException exception) {
            IllegalStateException cleanupFailure =
                    new IllegalStateException("failed to clean up MariaDB smoke-test " + fixture, exception);
            if (priorFailure == null) {
                return cleanupFailure;
            }
            priorFailure.addSuppressed(cleanupFailure);
        }
        return priorFailure;
    }
}
