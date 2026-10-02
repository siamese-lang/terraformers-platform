package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
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
                "generated Terraform failed Terraform CLI validation");
        assertThat(validation.reason()).doesNotContain("definitely_not_a_real_argument");
        assertTempRootEmpty();
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
        assertThat(validation.reason()).contains("offline Terraform initialization");
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
        assertThat(validation.reason()).isEqualTo("Terraform CLI initialization timed out");
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
        assertThat(timeout.reason()).isEqualTo("Terraform CLI validation timed out");
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

    private TerraformCliValidator validator(RecordingExecutor executor) {
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
