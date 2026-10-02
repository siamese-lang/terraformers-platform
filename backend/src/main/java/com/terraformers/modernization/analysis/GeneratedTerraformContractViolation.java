package com.terraformers.modernization.analysis;

import java.util.Objects;

public class GeneratedTerraformContractViolation extends IllegalStateException {

    public enum Reason {
        MODULE_BLOCK,
        RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT,
        RESOURCE_OUTSIDE_REQUEST_SCHEMA_ENVELOPE
    }

    private final Reason reason;

    public GeneratedTerraformContractViolation(Reason reason) {
        super(messageFor(reason));
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    private static String messageFor(Reason reason) {
        return switch (Objects.requireNonNull(reason, "reason")) {
            case MODULE_BLOCK -> "module blocks are outside the executable contract";
            case RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT ->
                    "generated resource is outside the AWS provider contract";
            case RESOURCE_OUTSIDE_REQUEST_SCHEMA_ENVELOPE ->
                    "generated resource is outside the request schema envelope";
        };
    }
}
