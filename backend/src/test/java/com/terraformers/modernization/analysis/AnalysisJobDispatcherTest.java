package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnalysisJobDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void submissionDoesNotClaimUntilCapturedRunnableStartsAndSuppressesLocalDuplicate() {
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        AnalysisJobRunner runner = mock(AnalysisJobRunner.class);
        AtomicReference<Runnable> captured = new AtomicReference<>();
        Executor executor = captured::set;
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalysisJobDispatcher dispatcher = dispatcher(state, runner, executor, registry);

        assertThat(dispatcher.submit("job-1")).isTrue();
        assertThat(dispatcher.submit("job-1")).isFalse();
        verify(runner, never()).run("job-1");
        assertThat(dispatcher.isLocallySubmitted("job-1")).isTrue();

        captured.get().run();

        verify(runner).run("job-1");
        assertThat(dispatcher.isLocallySubmitted("job-1")).isFalse();
        assertThat(registry.find("terraformers.analysis.dispatch")
                .tags("outcome", "duplicate_suppressed").counter().count()).isEqualTo(1);
    }

    @Test
    void rejectionClearsMarkerAndLeavesSameJobEligibleForLaterSubmission() {
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        AnalysisJobRunner runner = mock(AnalysisJobRunner.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AnalysisJobDispatcher rejected = dispatcher(state, runner,
                task -> { throw new RejectedExecutionException("full"); }, registry);

        assertThat(rejected.submit("job-1")).isFalse();
        assertThat(rejected.isLocallySubmitted("job-1")).isFalse();
        verify(state, never()).claimEligible(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(registry.find("terraformers.analysis.jobs").counter()).isNull();
        assertThat(registry.find("terraformers.analysis.dispatch")
                .tags("outcome", "executor_rejected").counter().count()).isEqualTo(1);
    }

    @Test
    void periodicScanUsesConfiguredBoundAndSubmitsCandidates() {
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        AnalysisJobRunner runner = mock(AnalysisJobRunner.class);
        Executor direct = Runnable::run;
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(state.findEligibleJobIds(NOW, 4)).thenReturn(List.of("job-1", "job-2"));
        AnalysisJobDispatcher dispatcher = dispatcher(state, runner, direct, registry);

        dispatcher.dispatchEligible();

        verify(runner).run("job-1");
        verify(runner).run("job-2");
    }

    private AnalysisJobDispatcher dispatcher(AnalysisJobStateService state, AnalysisJobRunner runner,
            Executor executor, SimpleMeterRegistry registry) {
        return new AnalysisJobDispatcher(state, runner, executor, new AnalysisRuntimeProperties(),
                new AnalysisObservability(registry), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
