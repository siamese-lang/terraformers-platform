package com.terraformers.modernization.analysis;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class AnalysisExecutorConfig {
    @Bean(name = "analysisJobExecutor")
    @ConditionalOnMissingBean(name = "analysisJobExecutor")
    public Executor analysisJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("analysis-job-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean
    public Clock analysisClock() {
        return Clock.systemUTC();
    }

    @Bean(name = "analysisLeaseScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService analysisLeaseScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "analysis-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
