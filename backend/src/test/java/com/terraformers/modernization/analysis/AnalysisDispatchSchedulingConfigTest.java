package com.terraformers.modernization.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class AnalysisDispatchSchedulingConfigTest {

    @Test
    void schedulesFromBoundTwoSecondDuration() {
        assertConfiguredDelay(Duration.ofSeconds(2));
    }

    @Test
    void schedulesFromBoundShorterDuration() {
        assertConfiguredDelay(Duration.ofMillis(500));
    }

    @Test
    void disabledNewDispatchRetainsDeadlineScan() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setDispatchEnabled(false);
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);
        new AnalysisDispatchSchedulingConfig(mock(AnalysisJobDispatcher.class),
                mock(AnalysisResultCleanupDispatcher.class), properties,
                mock(TaskScheduler.class)).configureTasks(registrar);

        verify(registrar).addFixedDelayTask(any(Runnable.class), any(Duration.class));
    }

    @Test
    void capturedLightweightTickSweepsBeforeDiscoveryEvenWithFullWorkerPoolAndSchedulerLag() {
        var state = mock(AnalysisJobStateService.class);
        var runner = mock(AnalysisJobRunner.class);
        var properties = new AnalysisRuntimeProperties();
        var accepted = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var time = new java.util.concurrent.atomic.AtomicReference<>(accepted.plusSeconds(479));
        var clock = mock(java.time.Clock.class);
        org.mockito.Mockito.when(clock.instant()).thenAnswer(invocation -> time.get());
        var committed = new java.util.concurrent.atomic.AtomicReference<java.time.Instant>();
        org.mockito.Mockito.when(state.terminalizeIneligible(any())).thenAnswer(invocation -> {
            java.time.Instant inspection = invocation.getArgument(0);
            if (!inspection.isBefore(accepted.plusSeconds(480))) {
                time.set(inspection.plusSeconds(1)); // declared short-DB transition bound T
                committed.set(time.get());
            }
            return new AnalysisJobStateService.SweepResult(0, 0);
        });
        org.mockito.Mockito.when(state.findEligibleJobIds(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.List.of("another-job"));
        var dispatcher = new AnalysisJobDispatcher(state, runner, task -> {
            throw new java.util.concurrent.RejectedExecutionException("worker capacity exhausted");
        }, properties, new AnalysisObservability(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), clock);
        var registrar = mock(ScheduledTaskRegistrar.class);
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        new AnalysisDispatchSchedulingConfig(dispatcher, mock(AnalysisResultCleanupDispatcher.class),
                properties, mock(TaskScheduler.class)).configureTasks(registrar);
        verify(registrar).addFixedDelayTask(callback.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(2)));
        callback.getValue().run();
        org.assertj.core.api.Assertions.assertThat(committed.get()).isNull();
        // Deadline occurs just after inspection: remaining prior tick B=1s, fixed delay P=2s,
        // operating scheduler ready-task lag S=1s. This next real registered callback adds T=1s.
        time.set(accepted.plusSeconds(484));
        callback.getValue().run();
        org.assertj.core.api.Assertions.assertThat(committed.get()).isEqualTo(accepted.plusSeconds(485));
        var order = org.mockito.Mockito.inOrder(state);
        order.verify(state).terminalizeIneligible(accepted.plusSeconds(479));
        order.verify(state).findEligibleJobIds(accepted.plusSeconds(479), 4);
        order.verify(state).terminalizeIneligible(accepted.plusSeconds(484));
        order.verify(state).findEligibleJobIds(accepted.plusSeconds(484), 4);
        verify(runner, never()).run(any());
        properties.setDispatchEnabled(false);
        dispatcher.dispatchEligible();
        verify(state).terminalizeIneligible(accepted.plusSeconds(485));
    }

    private void assertConfiguredDelay(Duration interval) {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setDispatchPollInterval(interval);
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        new AnalysisDispatchSchedulingConfig(mock(AnalysisJobDispatcher.class),
                mock(AnalysisResultCleanupDispatcher.class), properties, scheduler)
                .configureTasks(registrar);

        verify(registrar).setTaskScheduler(scheduler);
        verify(registrar).addFixedDelayTask(any(Runnable.class), org.mockito.ArgumentMatchers.eq(interval));
    }
}
