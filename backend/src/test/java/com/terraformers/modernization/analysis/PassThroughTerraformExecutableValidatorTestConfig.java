package com.terraformers.modernization.analysis;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class PassThroughTerraformExecutableValidatorTestConfig {

    @Bean
    @Primary
    TerraformExecutableValidator terraformExecutableValidator() {
        return candidate -> new TerraformDraftValidation(true, candidate, null);
    }
}
