package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@Import(AnalysisJobStateService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnalysisJobStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired AnalysisJobRepository repository;
    @Autowired AnalysisJobStateService stateService;
    @MockBean AnalysisJobOrchestrator orchestrator;
    @MockBean AnalysisObservability observability;
    @MockBean Clock clock;
    @Autowired JdbcTemplate jdbc;
    private final AtomicReference<Instant> time = new AtomicReference<>(NOW);
    @BeforeEach void resetTime() {
        time.set(NOW);
        when(clock.instant()).thenAnswer(invocation -> time.get());
    }
    private final List<String> committedJobIds = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedJobs() {
        committedJobIds.forEach(repository::deleteById);
    }

    @Test
    void staleGenerationCannotCreateRelationalMetadataOrSucceedAfterReclaim() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(1L); job.setSourceFileId(2L); job.setSourceBucket("bucket");
        job.setSourceKey("source.png"); job.setStatus(AnalysisJobStatus.PENDING);
        String id = saveCommitted(job);
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(10))).isPresent();
        assertThat(stateService.claimEligible(id, NOW.plusSeconds(10), NOW.plusSeconds(70))).isEmpty();
        // Existing legacy generations remain fenced; production no longer creates a second attempt.
        AnalysisJobEntity legacy = repository.findById(id).orElseThrow();
        legacy.setAttemptCount(2); legacy.setClaimGeneration(2); legacy.setLeaseExpiresAt(NOW.plusSeconds(70));
        repository.saveAndFlush(legacy);

        AnalysisJobEntity reclaimed = repository.findById(id).orElseThrow();
        assertThat(reclaimed.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
        assertThat(reclaimed.getClaimGeneration()).isEqualTo(2);

        boolean finalized = stateService.markSucceededOwned(id, 1, NOW.plusSeconds(11),
                org.mockito.Mockito.mock(AnalysisResult.class),
                new com.terraformers.modernization.storage.ObjectReference("bucket", "key"),
                new com.terraformers.modernization.storage.ObjectWriteResult("test", false, "bucket", "key", null));

        assertThat(finalized).isFalse();
        verify(orchestrator, never()).storeTerraformDraft(any(), any());
        AnalysisJobEntity current = repository.findById(id).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(current.getAttemptCount()).isEqualTo(2);
        assertThat(current.getClaimGeneration()).isEqualTo(2);
        assertThat(current.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(70));
        assertThat(current.getResultFileId()).isNull();
        assertThat(current.getResultObjectKey()).isNull();
    }

    @Test
    void heartbeatMovesLeaseForwardWithoutChangingAttemptOrGeneration() {
        AnalysisJobEntity job = pendingJob();
        String id = saveCommitted(job);
        Instant firstLease = NOW.plusSeconds(60);
        Instant renewedLease = NOW.plusSeconds(90);
        assertThat(stateService.claimEligible(id, NOW, firstLease)).isPresent();
        AnalysisJobEntity claimed = repository.findById(id).orElseThrow();
        assertThat(claimed.getLeaseExpiresAt()).isEqualTo(firstLease);
        assertThat(claimed.getAttemptCount()).isEqualTo(1);
        assertThat(claimed.getClaimGeneration()).isEqualTo(1);

        assertThat(stateService.renewLease(id, 1, NOW.plusSeconds(20), renewedLease)).isTrue();

        AnalysisJobEntity renewed = repository.findById(id).orElseThrow();
        assertThat(renewed.getLeaseExpiresAt()).isEqualTo(renewedLease);
        assertThat(renewed.getAttemptCount()).isEqualTo(1);
        assertThat(renewed.getClaimGeneration()).isEqualTo(1);
    }

    @Test
    void currentOwnerWritesAndFinalizesTheExactDurableIntent() {
        AnalysisJobEntity job = pendingJob();
        String id = saveCommitted(job);
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(60))).isPresent();
        String key = "analysis-results/1/" + id + "/main.tf";
        assertThat(stateService.recordResultObjectIntentOwned(id, 1, NOW, "bucket", key)).isTrue();
        var reference = new com.terraformers.modernization.storage.ObjectReference("bucket", key);
        var quality = quality(EvidenceQualityAssessment.QualityStatus.DEGRADED,
                EvidenceQualityAssessment.Reason.REQUIRED_EVIDENCE_NOT_RETRIEVED);
        var result = new AnalysisResult("stub", "resource {}", "summary", List.of(), List.of(), List.of(),
                List.of(), quality);
        var writeResult = new com.terraformers.modernization.storage.ObjectWriteResult(
                "metadata-only", false, "bucket", key, null);
        var resultFile = org.mockito.Mockito.mock(
                com.terraformers.modernization.projectcore.ProjectFileEntity.class);
        when(resultFile.getFileId()).thenReturn(44L);
        when(orchestrator.storeTerraformDraft(reference, result)).thenReturn(writeResult);
        when(orchestrator.registerGeneratedTerraform(1L, result, writeResult)).thenReturn(resultFile);
        doAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            entity.setStatus(AnalysisJobStatus.SUCCEEDED);
            entity.setResultFileId(44L);
            entity.setResultObjectKey(key);
            return null;
        }).when(orchestrator).markSucceeded(any(), any(), any(), any());

        assertThat(stateService.markSucceededOwned(id, 1, NOW, result, reference, writeResult)).isTrue();

        AnalysisJobEntity succeeded = repository.findById(id).orElseThrow();
        assertThat(succeeded.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
        assertThat(succeeded.getResultFileId()).isEqualTo(44L);
        assertThat(succeeded.getResultObjectKey()).isEqualTo(key);
        assertThat(succeeded.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.NOT_REQUIRED);
        assertThat(succeeded.getLeaseExpiresAt()).isNull();
        assertThat(succeeded.getQualityContractVersion()).isEqualTo("evidence-quality-v1");
        assertThat(succeeded.getTechnicalStatus()).isEqualTo(EvidenceQualityAssessment.TechnicalStatus.PASS);
        assertThat(succeeded.getQualityStatus()).isEqualTo(EvidenceQualityAssessment.QualityStatus.DEGRADED);
        assertThat(succeeded.qualityReasonValues())
                .containsExactly(EvidenceQualityAssessment.Reason.REQUIRED_EVIDENCE_NOT_RETRIEVED);
    }

    @Test
    void staleGenerationCannotMutateTerminalQualityAndRetryHasNoSnapshot() {
        String id = saveCommitted(pendingJob());
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(10))).isPresent();
        assertThat(stateService.claimEligible(id, NOW.plusSeconds(10), NOW.plusSeconds(70))).isEmpty();
        // Existing legacy generations remain fenced; production no longer creates a second attempt.
        AnalysisJobEntity legacy = repository.findById(id).orElseThrow();
        legacy.setAttemptCount(2); legacy.setClaimGeneration(2); legacy.setLeaseExpiresAt(NOW.plusSeconds(70));
        repository.saveAndFlush(legacy);
        var blocked = quality(EvidenceQualityAssessment.QualityStatus.UNKNOWN,
                EvidenceQualityAssessment.Reason.PROVIDER_CONTENT_BLOCKED);

        assertThat(stateService.markFailedOwned(id, 1, NOW.plusSeconds(11), "stale", blocked)).isFalse();
        assertThat(repository.findById(id).orElseThrow().getQualityContractVersion()).isNull();
        assertThat(stateService.scheduleRetryOwned(id, 2, NOW.plusSeconds(11), NOW.plusSeconds(20))).isFalse();
        assertThat(repository.findById(id).orElseThrow().getQualityContractVersion()).isNull();
    }

    @Test
    void onlyOwnedFailurePublishesFailedProgress() {
        AnalysisJobEntity job = pendingJob();
        String id = saveCommitted(job);
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(60))).isPresent();

        assertThat(stateService.markFailedOwned(id, 99, NOW.plusSeconds(1), "stale")).isFalse();
        assertThat(repository.findById(id).orElseThrow().getTerminalAt()).isNull();
        verify(orchestrator, never()).publishFailedProgress(any());

        assertThat(stateService.markFailedOwned(id, 1, NOW.plusSeconds(1), "owned")).isTrue();
        verify(orchestrator, times(1)).publishFailedProgress(any());
        AnalysisJobEntity failed = repository.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("owned");
        assertThat(failed.getTerminalAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(failed.getLeaseExpiresAt()).isNull();
        assertThat(failed.getNextAttemptAt()).isNull();
    }

    @Test
    void pendingAtOriginalCutoffFailsWithoutProviderAndInsideCutoffRemainsEligible() {
        String oldId = saveCommitted(pendingJob());
        time.set(NOW.plusSeconds(479));
        assertThat(stateService.terminalizeIneligible(time.get()).expired()).isZero();
        assertThat(repository.findById(oldId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        time.set(NOW.plusSeconds(480));
        var runner = org.mockito.Mockito.mock(AnalysisJobRunner.class);
        var dispatcher = new AnalysisJobDispatcher(stateService, runner, Runnable::run,
                new AnalysisRuntimeProperties(), new AnalysisObservability(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), clock);
        dispatcher.dispatchEligible();
        AnalysisJobEntity failed = repository.findById(oldId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.getAttemptCount()).isZero();
        assertThat(failed.getCreatedAt()).isEqualTo(NOW);
        assertThat(failed.getTerminalAt()).isEqualTo(NOW.plusSeconds(480));
        assertThat(failed.getFailureReason()).isEqualTo(AnalysisJobRunner.DEADLINE_FAILURE_REASON);
        assertThat(failed.getTechnicalStatus()).isEqualTo(EvidenceQualityAssessment.TechnicalStatus.FAIL);
        assertThat(failed.getQualityStatus()).isEqualTo(EvidenceQualityAssessment.QualityStatus.UNKNOWN);
        assertThat(failed.qualityReasonValues()).isEmpty(); // no provider call/timeout is fabricated
        verify(runner, never()).run(any());
        assertThat(stateService.claimEligible(oldId, time.get(), time.get().plusSeconds(60))).isEmpty();
    }

    @Test
    void blockedProviderFailsWhileHealthyHeartbeatsCannotResetAcceptedAgeAndLateReturnCannotSucceed() throws Exception {
        String id = saveCommitted(pendingJob());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(orchestrator.executeProviderAndValidate(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return draft();
        });
        var heartbeat = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        var runner = runner(heartbeat);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var running = worker.submit(() -> runner.run(id));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            for (int seconds = 20; seconds <= 460; seconds += 20) {
                time.set(NOW.plusSeconds(seconds)); heartbeat.getValue().run();
            }
            time.set(NOW.plusSeconds(479)); heartbeat.getValue().run();
            assertThat(stateService.terminalizeIneligible(time.get()).expired()).isZero();
            time.set(NOW.plusSeconds(480));
            assertThat(stateService.terminalizeIneligible(time.get()).expired()).isEqualTo(1);
            assertThat(running.isDone()).isFalse();
            heartbeat.getValue().run();
            assertThat(stateService.renewLease(id, 1, time.get(), time.get().plusSeconds(60))).isFalse();
            assertThat(stateService.recordResultObjectIntentOwned(id, 1, time.get(), "bucket", "late.tf")).isFalse();
            release.countDown(); running.get(5, java.util.concurrent.TimeUnit.SECONDS);
            runner.run(id);
            AnalysisJobEntity current = repository.findById(id).orElseThrow();
            assertThat(current.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(current.getCreatedAt()).isEqualTo(NOW);
            assertThat(current.getTerminalAt()).isEqualTo(NOW.plusSeconds(480));
            assertThat(current.getAttemptCount()).isEqualTo(1);
            assertThat(current.getClaimGeneration()).isEqualTo(1);
            assertThat(current.getLeaseExpiresAt()).isNull();
            verify(orchestrator, times(1)).executeProviderAndValidate(any());
            verify(orchestrator, never()).storeTerraformDraft(any(), any());
            verify(orchestrator, never()).publishSucceededProgress(any());
            verify(observability).terminalSweepFailures(org.mockito.ArgumentMatchers.eq("accepted_age_cutoff"),
                    org.mockito.ArgumentMatchers.eq(1), any());
        } finally {
            release.countDown(); worker.shutdown();
            assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void blockedExternalWriteDoesNotHoldJobLockAndLateObjectIsRearmedAfterEarlierCleanup() throws Exception {
        String id = saveCommitted(pendingJob());
        var reference = new com.terraformers.modernization.storage.ObjectReference("bucket", "late.tf");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var objectPresent = new java.util.concurrent.atomic.AtomicBoolean();
        when(orchestrator.executeProviderAndValidate(any())).thenReturn(draft());
        when(orchestrator.resolveResultObjectReference(any())).thenReturn(reference);
        when(orchestrator.storeTerraformDraft(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            objectPresent.set(true);
            return write(reference);
        });
        doAnswer(invocation -> { objectPresent.set(false); return null; }).when(orchestrator).removeStoredDraft(reference);
        var heartbeat = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        var runner = runner(heartbeat);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var writing = worker.submit(() -> runner.run(id));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            time.set(NOW.plusSeconds(480));
            // The original archived before-state probe blocks here. The corrected write holds no DB lock.
            assertThat(stateService.terminalizeIneligible(time.get()).expired()).isEqualTo(1);
            assertThat(writing.isDone()).isFalse();
            assertThat(stateService.recoverPendingCleanup(id)).isTrue();
            assertThat(repository.findById(id).orElseThrow().getResultCleanupStatus())
                    .isEqualTo(AnalysisResultCleanupStatus.COMPLETED);
            release.countDown(); writing.get(5, java.util.concurrent.TimeUnit.SECONDS);
            AnalysisJobEntity failed = repository.findById(id).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(failed.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.COMPLETED);
            assertThat(failed.getResultObjectIntentKey()).isEqualTo("late.tf");
            assertThat(failed.getResultFileId()).isNull();
            assertThat(failed.getResultObjectKey()).isNull();
            assertThat(objectPresent).isFalse();
            verify(orchestrator, times(2)).removeStoredDraft(reference);
            verify(orchestrator, never()).registerGeneratedTerraform(any(), any(), any());
            verify(orchestrator, never()).publishSucceededProgress(any());
        } finally {
            release.countDown(); worker.shutdown();
            assertThat(worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void lateCleanupFailureRetainsMatchingDurableAccountability() {
        String id = saveCommitted(pendingJob());
        var reference = new com.terraformers.modernization.storage.ObjectReference("bucket", "late-failure.tf");
        when(orchestrator.executeProviderAndValidate(any())).thenReturn(draft());
        when(orchestrator.resolveResultObjectReference(any())).thenReturn(reference);
        when(orchestrator.storeTerraformDraft(any(), any())).thenAnswer(invocation -> {
            time.set(NOW.plusSeconds(480)); stateService.terminalizeIneligible(time.get());
            return write(reference);
        });
        org.mockito.Mockito.doThrow(new IllegalStateException("injected delete failure"))
                .when(orchestrator).removeStoredDraft(reference);
        runner(org.mockito.ArgumentCaptor.forClass(Runnable.class)).run(id);
        AnalysisJobEntity current = repository.findById(id).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(current.getResultCleanupStatus()).isEqualTo(AnalysisResultCleanupStatus.PENDING);
        assertThat(current.getResultObjectIntentKey()).isEqualTo(reference.key());
        assertThat(stateService.findPendingCleanupJobIds(4)).contains(id);
    }

    @Test
    void expiredAttemptCannotBeReclaimedAndTerminatesRatherThanStranding() {
        String id = saveCommitted(pendingJob());
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(60))).isPresent();
        time.set(NOW.plusSeconds(60));
        assertThat(stateService.claimEligible(id, time.get(), time.get().plusSeconds(60))).isEmpty();
        assertThat(stateService.terminalizeIneligible(time.get()).exhausted()).isEqualTo(1);
        var failed = repository.findById(id).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getClaimGeneration()).isEqualTo(1);
        assertThat(failed.getFailureReason()).isEqualTo(AnalysisJobRunner.ATTEMPT_FAILURE_REASON);
        assertThat(stateService.findEligibleJobIds(time.get(), 4)).doesNotContain(id);
    }

    @Test
    void deadlineCrossingDuringShortMetadataTransactionRollsBackAndPublishesNoSuccess() {
        String id = saveCommitted(pendingJob());
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(600))).isPresent();
        var reference = new com.terraformers.modernization.storage.ObjectReference("bucket", "commit.tf");
        stateService.recordResultObjectIntentOwned(id, 1, NOW, reference.bucket(), reference.key());
        time.set(NOW.plusSeconds(470));
        doAnswer(invocation -> {
            jdbc.update("update analysis_jobs set result_file_id = 991 where id = ?", id);
            time.set(NOW.plusSeconds(480));
            return org.mockito.Mockito.mock(com.terraformers.modernization.projectcore.ProjectFileEntity.class);
        }).when(orchestrator).registerGeneratedTerraform(any(), any(), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stateService.markSucceededOwned(
                id, 1, NOW.plusSeconds(470), draft(), reference, write(reference)))
                .isInstanceOf(AnalysisResultFinalizationException.class)
                .hasCauseInstanceOf(AnalysisJobBudgetExceededException.class);
        var rolledBack = repository.findById(id).orElseThrow();
        assertThat(rolledBack.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(rolledBack.getResultFileId()).isNull();
        verify(orchestrator, never()).publishSucceededProgress(any());
        assertThat(stateService.terminalizeIneligible(time.get()).expired()).isEqualTo(1);
    }

    @Test
    void successPublicationObservesCommittedStateAndPublisherFailureCannotReverseSuccess() throws Exception {
        String id = saveCommitted(pendingJob());
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(600))).isPresent();
        var reference = new com.terraformers.modernization.storage.ObjectReference("bucket", "success.tf");
        stateService.recordResultObjectIntentOwned(id, 1, NOW, reference.bucket(), reference.key());
        prepareSuccess(reference);
        doAnswer(invocation -> {
            // Separate autocommit connection sees only committed success, not this transaction's flush.
            try (var connection = jdbc.getDataSource().getConnection();
                    var statement = connection.prepareStatement("select status from analysis_jobs where id = ?")) {
                statement.setString(1, id);
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue(); assertThat(row.getString(1)).isEqualTo("SUCCEEDED");
                }
            }
            throw new IllegalStateException("injected publisher failure after commit");
        }).when(orchestrator).publishSucceededProgress(any());
        assertThat(stateService.markSucceededOwned(id, 1, NOW, draft(), reference, write(reference))).isTrue();
        var succeeded = repository.findById(id).orElseThrow();
        assertThat(succeeded.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
        assertThat(stateService.rearmResultCleanup(id, 1, reference, NOW)).isFalse();
        verify(orchestrator, times(1)).publishSucceededProgress(any());
        verify(orchestrator, never()).storeTerraformDraft(any(), any());
        assertThat(stateService.terminalizeIneligible(NOW.plusSeconds(500)).expired()).isZero();
    }

    @Test
    void deadlineCrossingBeforeCommitRollsBackFlushedSuccessAndNoEventEscapes() {
        String id = saveCommitted(pendingJob());
        assertThat(stateService.claimEligible(id, NOW, NOW.plusSeconds(600))).isPresent();
        var reference = new com.terraformers.modernization.storage.ObjectReference("bucket", "before-commit.tf");
        stateService.recordResultObjectIntentOwned(id, 1, NOW, reference.bucket(), reference.key());
        doAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            entity.setStatus(AnalysisJobStatus.SUCCEEDED); entity.setResultObjectKey(reference.key());
            time.set(NOW.plusSeconds(480));
            return null;
        }).when(orchestrator).markSucceeded(any(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> stateService.markSucceededOwned(
                id, 1, NOW, draft(), reference, write(reference))).isInstanceOf(AnalysisJobBudgetExceededException.class);
        var current = repository.findById(id).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
        assertThat(current.getResultObjectKey()).isNull();
        assertThat(current.getQualityContractVersion()).isNull();
        verify(orchestrator, never()).publishSucceededProgress(any());
    }

    private AnalysisResult draft() {
        return new AnalysisResult("stub", "resource {}", "summary", List.of(), List.of(), List.of(), List.of());
    }

    private com.terraformers.modernization.storage.ObjectWriteResult write(
            com.terraformers.modernization.storage.ObjectReference ref) {
        return new com.terraformers.modernization.storage.ObjectWriteResult("test", false, ref.bucket(), ref.key(), null);
    }

    private void prepareSuccess(com.terraformers.modernization.storage.ObjectReference reference) {
        doAnswer(invocation -> {
            AnalysisJobEntity entity = invocation.getArgument(0);
            entity.setStatus(AnalysisJobStatus.SUCCEEDED); entity.setResultObjectKey(reference.key());
            return null;
        }).when(orchestrator).markSucceeded(any(), any(), any(), any());
    }

    private AnalysisJobRunner runner(org.mockito.ArgumentCaptor<Runnable> heartbeat) {
        var scheduler = org.mockito.Mockito.mock(java.util.concurrent.ScheduledExecutorService.class);
        org.mockito.Mockito.doReturn(org.mockito.Mockito.mock(java.util.concurrent.ScheduledFuture.class))
                .when(scheduler).scheduleAtFixedRate(heartbeat.capture(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(), any());
        return new AnalysisJobRunner(orchestrator, stateService,
                new AnalysisObservability(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                new AnalysisRuntimeProperties(), scheduler, clock);
    }

    private AnalysisJobEntity pendingJob() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        job.setProjectId(1L);
        job.setSourceFileId(2L);
        job.setSourceBucket("bucket");
        job.setSourceKey("source.png");
        job.setStatus(AnalysisJobStatus.PENDING);
        return job;
    }

    private String saveCommitted(AnalysisJobEntity job) {
        String id = repository.saveAndFlush(job).getId();
        jdbc.update("update analysis_jobs set created_at = ?, updated_at = ? where id = ?",
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW), id);
        committedJobIds.add(id);
        return id;
    }

    private EvidenceQualityAssessment quality(EvidenceQualityAssessment.QualityStatus status,
            EvidenceQualityAssessment.Reason reason) {
        return new EvidenceQualityAssessment(EvidenceQualityAssessment.CONTRACT_VERSION,
                EvidenceQualityAssessment.TechnicalStatus.PASS,
                EvidenceQualityAssessment.KnowledgeStatus.COMPLETE, status,
                EvidenceQualityAssessment.ProjectDecisionStatus.UNKNOWN,
                EvidenceQualityAssessment.RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                List.of(reason), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
