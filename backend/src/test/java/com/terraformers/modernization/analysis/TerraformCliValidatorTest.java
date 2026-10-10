package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerraformCliValidatorTest {

    @TempDir
    Path tempRoot;

    @Test
    void realOfflineMissingProviderCannotBeMistakenForHclSchemaFailure() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("pt8a.testTerraform") != null,
                "uses already checked Dockerfile-pinned local executable when supplied; no test download");
        Path emptyPlugins = Files.createDirectory(tempRoot.resolve("empty-plugins"));
        try {
            var real = new TerraformCliValidator(new ObjectMapper(), new TerraformCliValidator.ProcessCommandExecutor(),
                    System.getProperty("pt8a.testTerraform"), emptyPlugins, Duration.ofSeconds(60), Duration.ofSeconds(20), tempRoot);
            String candidate = """
                    terraform {
                      required_providers {
                        aws = { source = "hashicorp/aws", version = "=5.100.0" }
                      }
                    }
                    resource "aws_vpc" "valid" { cidr_block = "10.0.0.0/16" }
                    """;
            var failed = real.validate(candidate);
            assertThat(failed.reason()).startsWith("PROVIDER_CLOSURE:");
            assertThat(failed.executionPhase()).isEqualTo("init");
            assertThat(failed.sanitizedContent()).isEqualTo(candidate);
            assertThat(failed.exitCode()).isNotZero();
        } finally { Files.deleteIfExists(emptyPlugins); }
        assertTempRootEmpty();
    }

    @Test
    void commandExecutionFailureIsNotConfigurationOrTimeoutAndNeverReturnsExceptionText() {
        TerraformCliValidator.CommandExecutor failed = (command, dir, timeout) -> {
            throw new java.io.IOException("PRIVATE-EXECUTION-DETAIL");
        };
        var result = validator(failed).validate("resource \"aws_vpc\" \"x\" {}");
        assertThat(result.reason()).startsWith("COMMAND_EXECUTION:").doesNotContain("PRIVATE-EXECUTION-DETAIL");
        assertThat(result.executionPhase()).isEqualTo("init");
        assertThat(result.exitCode()).isNull();
        assertThat(result.elapsedMs()).isNotNegative();
        var malformed = validator(new RecordingExecutor(result(0, false, "ok"), result(1, false, "PRIVATE-RAW-JSON")))
                .validate("resource \"aws_vpc\" \"x\" {}");
        assertThat(malformed.reason()).startsWith("INTERNAL:").doesNotContain("PRIVATE-RAW-JSON");
        assertThat(malformed.executionPhase()).isEqualTo("validate");
        assertThat(malformed.exitCode()).isEqualTo(1);
    }

    @Test
    void initializationDiagnosticsSurviveTerminalExceptionMapping() {
        var invalid = validator(new RecordingExecutor(result(1, false, "Error: Invalid character\nPRIVATE-USER-VALUE")))
                .validate("resource \"aws_vpc\" \"x\" {}");
        var failure = TerraformValidationFailureException.fromSafeReason(invalid.reason(), invalid.diagnosticSummary());
        assertThat(failure.category()).isEqualTo(TerraformValidationFailureException.Category.INIT_CONFIGURATION);
        assertThat(failure.diagnosticSummary()).isEqualTo(invalid.diagnosticSummary());
        assertThat(failure.diagnosticSummary().diagnosticClasses()).containsExactly(TerraformDiagnosticSummary.DiagnosticClass.CONFIGURATION_SYNTAX);
        assertThat(failure.toString()).doesNotContain("PRIVATE-USER-VALUE");
    }

    @Test
    void acceptsValidCliDiagnosticsUsingOfflineBoundedCommandsAndCleansWorkspace() {
        RecordingExecutor executor = new RecordingExecutor(
                result(0, false, "init ok"),
                result(0, false, """
                        {"format_version":"1.0","valid":true,"error_count":0,
                         "warning_count":0,"diagnostics":[]}
                        """)
        );
        TerraformCliValidator validator = validator(executor);

        TerraformDraftValidation validation = validator.validate("""
                resource "aws_vpc" "main" {
                  cidr_block = "10.0.0.0/16"
                }
                """);

        assertThat(validation.valid()).isTrue();
        assertThat(executor.commands()).hasSize(2);
        assertThat(executor.commands().get(0))
                .containsExactly(
                        "terraform",
                        "init",
                        "-backend=false",
                        "-input=false",
                        "-get=false",
                        "-no-color",
                        "-plugin-dir=/plugins"
                )
                .doesNotContain("plan", "apply");
        assertThat(executor.commands().get(1))
                .containsExactly("terraform", "validate", "-json", "-no-color")
                .doesNotContain("plan", "apply");
        assertThat(executor.timeouts()).containsExactly(
                Duration.ofSeconds(3),
                Duration.ofSeconds(2)
        );
        assertThat(executor.workingDirectories()).hasSize(2);
        assertThat(executor.workingDirectories().get(0))
                .isEqualTo(executor.workingDirectories().get(1));
        assertThat(executor.sawMainTf()).containsOnly(true);
        assertTempRootEmpty();
    }

    @Test
    void rejectsProviderSchemaValidationFailure() {
        RecordingExecutor executor = new RecordingExecutor(
                result(0, false, "init ok"),
                result(1, false, """
                        {"format_version":"1.0","valid":false,"error_count":1,
                         "warning_count":0,"diagnostics":[{"severity":"error"}]}
                        """)
        );

        TerraformDraftValidation validation = validator(executor).validate("""
                resource "aws_vpc" "main" {
                  cidr_block = "10.0.0.0/16"
                  definitely_not_a_real_argument = true
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).isEqualTo(
                "VALIDATE_CONFIGURATION: generated Terraform failed Terraform CLI validation");
        assertThat(validation.reason()).doesNotContain("definitely_not_a_real_argument");
        assertTempRootEmpty();
    }

    @Test
    void reducesFixtureBackedDiagnosticsWithoutLeakingRawTerraformData() throws Exception {
        assertFixture("missing-required-argument.json",
                TerraformDiagnosticSummary.DiagnosticClass.MISSING_REQUIRED_ARGUMENT, 1, 0);
        assertFixture("unsupported-argument-or-block.json",
                TerraformDiagnosticSummary.DiagnosticClass.UNSUPPORTED_ARGUMENT_OR_BLOCK, 2, 1);
        assertFixture("undeclared-reference.json",
                TerraformDiagnosticSummary.DiagnosticClass.UNDECLARED_REFERENCE, 1, 0);
        assertFixture("unknown.json", TerraformDiagnosticSummary.DiagnosticClass.UNKNOWN, 1_000, 0);
    }

    @Test
    void producesDeterministicUniqueDiagnosticOrderingForRepresentativeC2Class() {
        String rawHcl = "SENTINEL_RAW_HCL";
        String json = """
                {"valid":false,"error_count":3,"warning_count":4,"diagnostics":[
                  {"severity":"error","summary":"Unsupported argument","detail":"SENTINEL_DETAIL"},
                  {"severity":"error","summary":"Missing required argument","detail":"SENTINEL_DETAIL"},
                  {"severity":"error","summary":"Unsupported block type","detail":"SENTINEL_DETAIL"}]}
                """;
        TerraformDraftValidation validation = validator(new RecordingExecutor(
                result(0, false, "init ok"), result(1, false, json))).validate(rawHcl);

        assertThat(validation.reason()).isEqualTo(
                "VALIDATE_CONFIGURATION: generated Terraform failed Terraform CLI validation");
        assertThat(validation.diagnosticSummary().diagnosticClasses()).containsExactly(
                TerraformDiagnosticSummary.DiagnosticClass.MISSING_REQUIRED_ARGUMENT,
                TerraformDiagnosticSummary.DiagnosticClass.UNSUPPORTED_ARGUMENT_OR_BLOCK);
        assertThat(validation.diagnosticSummary().errorCount()).isEqualTo(3);
        assertThat(validation.diagnosticSummary().warningCount()).isEqualTo(4);

        TerraformValidationFailureException failure = TerraformValidationFailureException.fromSafeReason(
                validation.reason(), validation.diagnosticSummary());
        assertThat(failure.category()).isEqualTo(
                TerraformValidationFailureException.Category.VALIDATE_CONFIGURATION);
        assertThat(failure.diagnosticSummary()).isEqualTo(validation.diagnosticSummary());
        assertThat(failure.getMessage()).doesNotContain(rawHcl, "SENTINEL_DETAIL");
    }

    @Test
    void failsClosedWhenInitializationFailsBeforeValidate() {
        RecordingExecutor executor = new RecordingExecutor(result(1, false, "provider unavailable"));

        TerraformDraftValidation validation = validator(executor).validate("""
                resource "aws_vpc" "main" {
                  cidr_block = "10.0.0.0/16"
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).isEqualTo("PROVIDER_CLOSURE: offline provider closure could not be satisfied");
        assertThat(executor.commands()).hasSize(1);
        assertTempRootEmpty();
    }

    @Test
    void failsClosedWhenInitializationTimesOutBeforeValidate() {
        RecordingExecutor executor = new RecordingExecutor(result(-1, true, ""));

        TerraformDraftValidation validation = validator(executor).validate("""
                resource "aws_vpc" "main" {
                  cidr_block = "10.0.0.0/16"
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.reason()).isEqualTo("INIT_TIMEOUT: Terraform CLI initialization timed out");
        assertThat(executor.commands()).hasSize(1);
        assertThat(executor.timeouts()).containsExactly(Duration.ofSeconds(3));
        assertTempRootEmpty();
    }

    @Test
    void failsClosedWhenValidationTimesOutAfterSuccessfulInitialization() {
        RecordingExecutor timeoutExecutor = new RecordingExecutor(
                result(0, false, "init ok"),
                result(-1, true, "")
        );
        TerraformDraftValidation timeout = validator(timeoutExecutor).validate("""
                resource "aws_vpc" "main" {
                  cidr_block = "10.0.0.0/16"
                }
                """);

        assertThat(timeout.valid()).isFalse();
        assertThat(timeout.reason()).isEqualTo("VALIDATE_TIMEOUT: Terraform CLI validation timed out");
        assertThat(timeoutExecutor.commands()).hasSize(2);
        assertThat(timeoutExecutor.timeouts()).containsExactly(
                Duration.ofSeconds(3),
                Duration.ofSeconds(2)
        );
        assertTempRootEmpty();
    }

    @Test
    void failsClosedOnMalformedDiagnostics() {
        RecordingExecutor malformedExecutor = new RecordingExecutor(
                result(0, false, "init ok"),
                result(0, false, "not-json")
        );
        TerraformDraftValidation malformed = validator(malformedExecutor).validate("""
                resource "aws_vpc" "main" {
                  cidr_block = "10.0.0.0/16"
                }
                """);

        assertThat(malformed.valid()).isFalse();
        assertThat(malformed.reason()).contains("malformed diagnostics");
        assertTempRootEmpty();
    }

    @Test
    void classifiesInitializationSyntaxFailureWithoutLeakingCapturedOutput() {
        String sensitive = "SECRET-FIXTURE-VALUE";
        TerraformDraftValidation validation = validator(new RecordingExecutor(
                result(1, false, "Error: Invalid character\n" + sensitive)))
                .validate("resource \"aws_vpc\" \"x\" {}");

        assertThat(validation.reason()).isEqualTo("INIT_CONFIGURATION: Terraform initialization/configuration failed");
        assertThat(validation.diagnosticSummary().diagnosticClasses())
                .containsExactly(TerraformDiagnosticSummary.DiagnosticClass.CONFIGURATION_SYNTAX);
        assertThat(validation.diagnosticSummary().errorCount()).isOne();
        assertThat(validation.diagnosticSummary().warningCount()).isZero();
        assertThat(validation.reason()).doesNotContain(sensitive);
        assertThat(validation.diagnosticSummary().toString()).doesNotContain(sensitive);
    }

    @Test
    void remoteModuleCannotTriggerModuleDownloadCommand() {
        RecordingExecutor executor = new RecordingExecutor(result(1, false, "module not installed"));

        TerraformDraftValidation validation = validator(executor).validate("""
                module "remote" {
                  source = "example/example/aws"
                }
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(executor.commands()).singleElement().satisfies(command -> {
            assertThat(command).contains("-get=false", "-backend=false", "-plugin-dir=/plugins");
            assertThat(command).doesNotContain("get", "plan", "apply");
        });
    }

    private void assertTempRootEmpty() {
        try (var paths = Files.list(tempRoot)) {
            assertThat(paths.toList()).isEmpty();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertFixture(String fixture,
                               TerraformDiagnosticSummary.DiagnosticClass expectedClass,
                               int errorCount, int warningCount) throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/terraform-diagnostics/" + fixture)) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        TerraformDraftValidation validation = validator(new RecordingExecutor(
                result(0, false, "init ok"), result(1, false, json)))
                .validate("SENTINEL_RAW_HCL");

        assertThat(validation.diagnosticSummary().diagnosticClasses()).containsExactly(expectedClass);
        assertThat(validation.diagnosticSummary().errorCount()).isEqualTo(errorCount);
        assertThat(validation.diagnosticSummary().warningCount()).isEqualTo(warningCount);
        assertThat(validation.reason()).doesNotContain(
                "SENTINEL_RAW_HCL", "SENTINEL_SUMMARY", "SENTINEL_DETAIL",
                "SENTINEL_SNIPPET", "SENTINEL_PATH", "SENTINEL_RESOURCE");
        assertTempRootEmpty();
    }

    private TerraformCliValidator validator(TerraformCliValidator.CommandExecutor executor) {
        return new TerraformCliValidator(
                new ObjectMapper(),
                executor,
                "terraform",
                Path.of("/plugins"),
                Duration.ofSeconds(3),
                Duration.ofSeconds(2),
                tempRoot
        );
    }

    private TerraformCliValidator.CommandResult result(int exitCode, boolean timedOut, String output) {
        return new TerraformCliValidator.CommandResult(exitCode, timedOut, output);
    }

    private static class RecordingExecutor implements TerraformCliValidator.CommandExecutor {
        private final Queue<TerraformCliValidator.CommandResult> results;
        private final List<List<String>> commands = new ArrayList<>();
        private final List<Path> workingDirectories = new ArrayList<>();
        private final List<Duration> timeouts = new ArrayList<>();
        private final List<Boolean> sawMainTf = new ArrayList<>();

        RecordingExecutor(TerraformCliValidator.CommandResult... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override
        public TerraformCliValidator.CommandResult run(
                List<String> command,
                Path workingDirectory,
                Duration timeout
        ) {
            commands.add(List.copyOf(command));
            workingDirectories.add(workingDirectory);
            timeouts.add(timeout);
            sawMainTf.add(Files.exists(workingDirectory.resolve("main.tf")));
            return results.remove();
        }

        List<List<String>> commands() {
            return commands;
        }

        List<Path> workingDirectories() {
            return workingDirectories;
        }

        List<Duration> timeouts() {
            return timeouts;
        }

        List<Boolean> sawMainTf() {
            return sawMainTf;
        }
    }
}
