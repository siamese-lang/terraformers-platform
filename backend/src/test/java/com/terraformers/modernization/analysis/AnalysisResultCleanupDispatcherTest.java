package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnalysisResultCleanupDispatcherTest {

    @Test
    void scansBoundedBatchAndSuppressesDuplicateUntilWorkCompletes() {
        AnalysisJobStateService state = mock(AnalysisJobStateService.class);
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setDispatchBatchSize(4);
        when(state.findPendingCleanupJobIds(4)).thenReturn(List.of("job-1"));
        AtomicReference<Runnable> task = new AtomicReference<>();
        Executor executor = task::set;
        AnalysisResultCleanupDispatcher dispatcher = new AnalysisResultCleanupDispatcher(state, executor,
                properties, new AnalysisObservability(new SimpleMeterRegistry()));

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
                properties, new AnalysisObservability(new SimpleMeterRegistry()));

        assertThat(dispatcher.submit("job-2")).isFalse();
        assertThat(dispatcher.isLocallySubmitted("job-2")).isFalse();
        assertThat(dispatcher.submit("job-2")).isFalse();
    }
}
