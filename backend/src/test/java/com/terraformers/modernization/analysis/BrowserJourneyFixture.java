package com.terraformers.modernization.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.TerraformersBackendApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Local browser fixture only. Does not replace the runner, provider, validator, or authorization. */
@Configuration(proxyBeanMethods = false)
@Profile("browser-journey")
public class BrowserJourneyFixture {
    public static void main(String[] args) {
        new SpringApplicationBuilder(BrowserJourneyFixture.class, TerraformersBackendApplication.class).run(args);
    }

    @Bean
    @Primary
    TerraformCliValidator localTerraformValidator(ObjectMapper mapper,
            @Value("${browser-journey.terraform-binary}") Path binary,
            @Value("${browser-journey.plugin-dir}") Path pluginDir) {
        // Same production implementation/process isolation/timeouts; only fixture-local tool paths differ.
        TerraformCliValidator validator = new TerraformCliValidator(mapper,
                new TerraformCliValidator.ProcessCommandExecutor(), binary.toString(), pluginDir,
                TerraformCliValidator.DEFAULT_INITIALIZATION_TIMEOUT,
                TerraformCliValidator.DEFAULT_VALIDATION_TIMEOUT, null);
        org.slf4j.LoggerFactory.getLogger(BrowserJourneyFixture.class).info(
                "Browser fixture real validator={} executor={} binary={} pluginDir={}",
                validator.getClass().getSimpleName(), "ProcessCommandExecutor", binary, pluginDir);
        return validator;
    }

    @Bean(name = "analysisJobExecutor")
    ThreadPoolTaskExecutor heldExecutor(@Value("${browser-journey.release-marker}") Path marker) {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AnalysisExecutorConfig().analysisJobExecutor();
        executor.setTaskDecorator(task -> () -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            while (!Files.exists(marker)) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Browser fixture hold exceeded 120 seconds; no work was claimed");
                }
                try {
                    Thread.sleep(25);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            task.run();
        });
        return executor;
    }
}
