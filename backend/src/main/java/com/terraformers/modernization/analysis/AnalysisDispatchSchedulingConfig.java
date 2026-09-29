package com.terraformers.modernization.analysis;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** Configures the lightweight durable scan directly from the bound Duration property. */
@Configuration
@EnableScheduling
public class AnalysisDispatchSchedulingConfig implements SchedulingConfigurer {

    private final AnalysisJobDispatcher dispatcher;
    private final AnalysisRuntimeProperties properties;
    private final AnalysisResultCleanupDispatcher cleanupDispatcher;
    private final TaskScheduler taskScheduler;

    public AnalysisDispatchSchedulingConfig(
            AnalysisJobDispatcher dispatcher,
            AnalysisResultCleanupDispatcher cleanupDispatcher,
            AnalysisRuntimeProperties properties,
            @Qualifier("analysisDispatchTaskScheduler") TaskScheduler analysisDispatchTaskScheduler
    ) {
        this.dispatcher = dispatcher;
        this.cleanupDispatcher = cleanupDispatcher;
        this.properties = properties;
        this.taskScheduler = analysisDispatchTaskScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler);
        if (properties.isDispatchEnabled()) {
            taskRegistrar.addFixedDelayTask(() -> {
                dispatcher.dispatchEligible();
                cleanupDispatcher.dispatchPending();
            }, properties.getDispatchPollInterval());
        }
    }

    @Bean(name = "analysisDispatchTaskScheduler")
    public static TaskScheduler analysisDispatchTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("analysis-dispatch-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
