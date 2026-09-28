package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.terraformers.modernization.storage.ObjectWriteResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalysisJobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void claimsWithLeaseInsideRunAndFinalizesWithAcquiredGeneration() {
        Fixture fixture = fixture();
        AnalysisJobEntity running = runningJob(1, 7);
        AnalysisJobExecution execution = execution();
        when(fixture.state.claimEligible("job-1", NOW, NOW.plusSeconds(60))).thenReturn(Optional.of(running));
        when(fixture.orchestrator.executeProviderAndStoreDraft(running)).thenReturn(execution);
        when(fixture.state.markSucceededOwned("job-1", 7, NOW, execution)).thenReturn(true);

        fixture.runner.run("job-1");

        verify(fixture.state).claimEligible("job-1", NOW, NOW.plusSeconds(60));
        verify(fixture.state).markSucceededOwned("job-1", 7, NOW, execution);
        assertThat(fixture.registry.find("terraformers.analysis.claims")
                .tags("outcome", "initial_claim").counter().count()).isEqualTo(1);
    }

    @Test
    void notEligibleDeliveryDoesNotExecuteProvider() {
        Fixture fixture = fixture();
        when(fixture.state.claimEligible("job-terminal", NOW, NOW.plusSeconds(60))).thenReturn(Optional.empty());

        fixture.runner.run("job-terminal");

        verify(fixture.orchestrator, never()).executeProviderAndStoreDraft(any());
        assertThat(fixture.registry.find("terraformers.analysis.claims")
                .tags("outcome", "not_claimed").counter().count()).isEqualTo(1);
    }

    @Test
    void transientProviderFailureIsFencedFailedOnceAndNeverSchedulesRetry() {
        Fixture fixture = fixture();
        AnalysisJobEntity running = runningJob(1, 3);
        AtomicInteger invocations = new AtomicInteger();
        when(fixture.state.claimEligible("job-transient", NOW, NOW.plusSeconds(60))).thenReturn(Optional.of(running));
        when(fixture.orchestrator.executeProviderAndStoreDraft(running)).thenAnswer(ignored -> {
            invocations.incrementAndGet();
            throw new AnalysisProviderTimeoutException(new SocketTimeoutException("temporary timeout"));
        });
        when(fixture.state.markFailedOwned("job-transient", 3, NOW,
                AnalysisJobRunner.TIMEOUT_FAILURE_REASON)).thenReturn(true);

        fixture.runner.run("job-transient");

        assertThat(invocations).hasValue(1);
        verify(fixture.state).markFailedOwned("job-transient", 3, NOW,
                AnalysisJobRunner.TIMEOUT_FAILURE_REASON);
        verify(fixture.state, never()).scheduleRetryOwned(any(), anyLong(), any(), any());
    }

    @Test
    void failedHeartbeatBlocksStaleSuccessAndFailureFinalization() {
        Fixture fixture = fixture();
        AnalysisJobEntity running = runningJob(2, 9);
        when(fixture.state.claimEligible("job-stale", NOW, NOW.plusSeconds(60))).thenReturn(Optional.of(running));
        when(fixture.state.renewLease("job-stale", 9, NOW, NOW.plusSeconds(60))).thenReturn(false);
        when(fixture.orchestrator.executeProviderAndStoreDraft(running)).thenAnswer(ignored -> {
            fixture.heartbeat.getValue().run();
            return execution();
        });

        fixture.runner.run("job-stale");

        verify(fixture.state, never()).markSucceededOwned(eq("job-stale"), anyLong(), any(), any());
        verify(fixture.state, never()).markFailedOwned(eq("job-stale"), anyLong(), any(), any());
        assertThat(fixture.registry.find("terraformers.analysis.lease.renewals")
                .tags("outcome", "lost").counter().count()).isEqualTo(1);
    }

    @Test
    void heartbeatRenewsSameGenerationWithoutCreatingAnotherAttempt() {
        Fixture fixture = fixture();
        AnalysisJobEntity running = runningJob(1, 4);
        AnalysisJobExecution execution = execution();
        when(fixture.state.claimEligible("job-long", NOW, NOW.plusSeconds(60))).thenReturn(Optional.of(running));
        when(fixture.state.renewLease("job-long", 4, NOW, NOW.plusSeconds(60))).thenReturn(true);
        when(fixture.orchestrator.executeProviderAndStoreDraft(running)).thenAnswer(ignored -> {
            fixture.heartbeat.getValue().run();
            return execution;
        });
        when(fixture.state.markSucceededOwned("job-long", 4, NOW, execution)).thenReturn(true);

        fixture.runner.run("job-long");

        verify(fixture.state).renewLease("job-long", 4, NOW, NOW.plusSeconds(60));
        verify(fixture.state).claimEligible("job-long", NOW, NOW.plusSeconds(60));
        assertThat(running.getAttemptCount()).isEqualTo(1);
        assertThat(running.getClaimGeneration()).isEqualTo(4);
        assertThat(fixture.registry.find("terraformers.analysis.lease.renewals")
                .tags("outcome", "renewed").counter().count()).isEqualTo(1);
    }

    private Fixture fixture() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked") ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleAtFixedRate(heartbeat.capture(), anyLong(), anyLong(), any())).thenReturn(future);
        AnalysisJobRunner runner = new AnalysisJobRunner(orchestrator, state,
                new AnalysisObservability(registry), properties, scheduler,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(orchestrator, state, registry, runner, heartbeat);
    }

    private AnalysisJobEntity runningJob(int attempt, long generation) {
        AnalysisJobEntity entity = new AnalysisJobEntity();
        entity.setProjectId(42L);
        entity.setSourceFileId(101L);
        entity.setSourceBucket("source-bucket");
        entity.setSourceKey("source/key.png");
        entity.setAttemptCount(attempt);
        entity.setClaimGeneration(generation);
        entity.prePersist();
        return entity;
    }

    private AnalysisJobExecution execution() {
        return new AnalysisJobExecution(
                new AnalysisResult("stub", "resource {}", "summary", List.of(), List.of(), List.of(), List.of()),
                new ObjectWriteResult("s3", true, "result-bucket", "analysis/main.tf", "etag"));
    }

    private record Fixture(AnalysisJobOrchestrator orchestrator, AnalysisJobStateService state,
            SimpleMeterRegistry registry, AnalysisJobRunner runner, ArgumentCaptor<Runnable> heartbeat) {}
}
