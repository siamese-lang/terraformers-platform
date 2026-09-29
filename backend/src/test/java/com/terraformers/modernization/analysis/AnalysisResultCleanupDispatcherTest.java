package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.terraformers.modernization.storage.ObjectReference;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnalysisResultCleanupDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void scansBoundedBatchAndSuppressesDuplicateUntilWorkCompletes() {
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setDispatchBatchSize(4);
        when(state.findPendingCleanupJobIds(4)).thenReturn(List.of("job-1"));
        AtomicReference<Runnable> task = new AtomicReference<>();
        Executor executor = task::set;
        AnalysisResultCleanupDispatcher dispatcher = new AnalysisResultCleanupDispatcher(state, executor,
                properties, new AnalysisObservability(new SimpleMeterRegistry()), CLOCK);

        dispatcher.dispatchPending();

        assertThat(dispatcher.isLocallySubmitted("job-1")).isTrue();
        assertThat(dispatcher.submit("job-1")).isFalse();
        when(state.recoverPendingCleanup("job-1")).thenReturn(true);
        task.get().run();
        assertThat(dispatcher.isLocallySubmitted("job-1")).isFalse();
        verify(state).findPendingCleanupJobIds(4);
        verify(state).recoverPendingCleanup("job-1");
    }

    @Test
    void rejectedSubmissionRemovesMarkerSoLaterScanCanRetry() {
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        Executor rejected = task -> { throw new RejectedExecutionException("full"); };
        AnalysisResultCleanupDispatcher dispatcher = new AnalysisResultCleanupDispatcher(state, rejected,
                properties, new AnalysisObservability(new SimpleMeterRegistry()), CLOCK);

        assertThat(dispatcher.submit("job-2")).isFalse();
        assertThat(dispatcher.isLocallySubmitted("job-2")).isFalse();
        assertThat(dispatcher.submit("job-2")).isFalse();
    }

    @Test
    void failedRecoveryIsDeferredAndCanSucceedOnLaterSubmission() {
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        ObjectReference reference = new ObjectReference("bucket", "analysis/job-3/main.tf");
        var tasks = new ArrayDeque<Runnable>();
        when(state.recoverPendingCleanup("job-3"))
                .thenThrow(new AnalysisJobStateService.CleanupRecoveryException(
                        reference, new IllegalStateException("remove failed")))
                .thenReturn(true);
        when(state.deferPendingCleanup("job-3", reference, NOW)).thenReturn(true);
        AnalysisResultCleanupDispatcher dispatcher = new AnalysisResultCleanupDispatcher(state, tasks::add,
                properties, new AnalysisObservability(new SimpleMeterRegistry()), CLOCK);

        assertThat(dispatcher.submit("job-3")).isTrue();
        tasks.remove().run();

        assertThat(dispatcher.isLocallySubmitted("job-3")).isFalse();
        verify(state).deferPendingCleanup("job-3", reference, NOW);
        assertThat(dispatcher.submit("job-3")).isTrue();
        tasks.remove().run();
        assertThat(dispatcher.isLocallySubmitted("job-3")).isFalse();
        verify(state, org.mockito.Mockito.times(2)).recoverPendingCleanup("job-3");
    }
}
