package com.terraformers.modernization.analysis;

public interface TerraformExecutableValidator {
    TerraformDraftValidation validate(String candidate);
}
