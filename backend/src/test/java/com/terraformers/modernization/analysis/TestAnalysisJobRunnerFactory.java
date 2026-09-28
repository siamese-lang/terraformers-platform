package com.terraformers.modernization.analysis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

final class TestAnalysisJobRunnerFactory {

    private TestAnalysisJobRunnerFactory() {}

    static AnalysisJobRunner create(AnalysisJobOrchestrator orchestrator,
            AnalysisJobStateService stateService, AnalysisObservability observability) {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
        return new AnalysisJobRunner(orchestrator, stateService, observability,
                new AnalysisRuntimeProperties(), scheduler, Clock.systemUTC());
    }
}
