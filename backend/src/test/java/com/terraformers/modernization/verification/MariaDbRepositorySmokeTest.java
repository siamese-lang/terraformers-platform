package com.terraformers.modernization.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.analysis.AnalysisJobEntity;
import com.terraformers.modernization.analysis.AnalysisJobRepository;
import com.terraformers.modernization.analysis.AnalysisJobStatus;
import com.terraformers.modernization.analysis.AnalysisMode;
import com.terraformers.modernization.collaboration.BoardEntity;
import com.terraformers.modernization.collaboration.BoardRepository;
import com.terraformers.modernization.collaboration.CommentEntity;
import com.terraformers.modernization.collaboration.CommentRepository;
import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.identity.UserRepository;
import com.terraformers.modernization.project.ProjectVisibility;
import com.terraformers.modernization.projectcore.OwnedProjectEntity;
import com.terraformers.modernization.projectcore.OwnedProjectRepository;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import com.terraformers.modernization.projectcore.ProjectFileRepository;
import com.terraformers.modernization.projectcore.ProjectStatus;
import java.time.Instant;
import java.util.List;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("prod")
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "^jdbc:.*")
@Transactional
class MariaDbRepositorySmokeTest {

    private static final long BARRIER_TIMEOUT_SECONDS = 10;
    private static final long CLAIM_TIMEOUT_SECONDS = 20;

    @Autowired
    private UserRepository userRepository;

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
