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
    void schedulesFromBoundOneHourDuration() {
        assertConfiguredDelay(Duration.ofHours(1));
    }

    @Test
    void disabledDispatchDoesNotRegisterPeriodicScan() {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setDispatchEnabled(false);
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);
        new AnalysisDispatchSchedulingConfig(mock(AnalysisJobDispatcher.class), properties,
                mock(TaskScheduler.class)).configureTasks(registrar);

        verify(registrar, never()).addFixedDelayTask(any(Runnable.class), any(Duration.class));
    }

    private void assertConfiguredDelay(Duration interval) {
        AnalysisRuntimeProperties properties = new AnalysisRuntimeProperties();
        properties.setDispatchPollInterval(interval);
        ScheduledTaskRegistrar registrar = mock(ScheduledTaskRegistrar.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        new AnalysisDispatchSchedulingConfig(mock(AnalysisJobDispatcher.class), properties, scheduler)
                .configureTasks(registrar);

        verify(registrar).setTaskScheduler(scheduler);
        verify(registrar).addFixedDelayTask(any(Runnable.class), org.mockito.ArgumentMatchers.eq(interval));
    }
}
