package com.terraformers.modernization.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TerraformCliValidator implements TerraformExecutableValidator {

    static final String DEFAULT_TERRAFORM_BINARY = "/usr/local/bin/terraform";
    static final Path DEFAULT_PLUGIN_DIR = Path.of("/opt/terraform-plugins");
    static final Duration DEFAULT_INITIALIZATION_TIMEOUT = Duration.ofSeconds(60);
    static final Duration DEFAULT_VALIDATION_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_CAPTURE_BYTES = 32 * 1024;

    private final ObjectMapper objectMapper;
    private final CommandExecutor executor;
    private final String terraformBinary;
    private final Path pluginDir;
    private final Duration initializationTimeout;
    private final Duration validationTimeout;
    private final Path tempRoot;

    @Autowired
    public TerraformCliValidator(ObjectMapper objectMapper) {
        this(objectMapper, new ProcessCommandExecutor(), DEFAULT_TERRAFORM_BINARY,
                DEFAULT_PLUGIN_DIR, DEFAULT_INITIALIZATION_TIMEOUT, DEFAULT_VALIDATION_TIMEOUT, null);
    }

    TerraformCliValidator(
            ObjectMapper objectMapper,
            CommandExecutor executor,
            String terraformBinary,
            Path pluginDir,
            Duration initializationTimeout,
            Duration validationTimeout,
            Path tempRoot
    ) {
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.terraformBinary = terraformBinary;
        this.pluginDir = pluginDir;
        this.initializationTimeout = initializationTimeout;
        this.validationTimeout = validationTimeout;
        this.tempRoot = tempRoot;
    }

    @Override
    public TerraformDraftValidation validate(String candidate) {
        String content = candidate == null ? "" : candidate;
        Path workspace = null;
        TerraformDraftValidation outcome;
        try {
            workspace = createWorkspace();
            Files.writeString(workspace.resolve("main.tf"), content, StandardCharsets.UTF_8);

            CommandResult init = executor.run(initCommand(), workspace, initializationTimeout);
            if (init.timedOut()) {
                outcome = failure(content, TerraformValidationFailureException.Category.INIT_TIMEOUT,
                        "Terraform CLI initialization timed out");
            } else if (init.exitCode() != 0) {
                TerraformValidationFailureException.Category category = providerClosureFailure(init.output())
                        ? TerraformValidationFailureException.Category.PROVIDER_CLOSURE
                        : TerraformValidationFailureException.Category.INIT_CONFIGURATION;
                outcome = category == TerraformValidationFailureException.Category.PROVIDER_CLOSURE
                        ? failure(content, category, "offline provider closure could not be satisfied")
                        : failure(
                                content,
                                category,
                                "Terraform initialization/configuration failed",
                                reduceInitializationDiagnostics(init.output())
                        );
            } else {
                outcome = validateInitializedWorkspace(content, workspace);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            outcome = failure(content, TerraformValidationFailureException.Category.INTERNAL,
                    "Terraform CLI validation was interrupted");
        } catch (IOException | RuntimeException exception) {
            outcome = failure(content, TerraformValidationFailureException.Category.INTERNAL,
                    "Terraform CLI validation could not be completed");
        }

        if (!deleteWorkspace(workspace)) {
            return failure(content, TerraformValidationFailureException.Category.INTERNAL,
                    "Terraform CLI validation workspace cleanup failed");
        }
        return outcome;
    }

    private TerraformDraftValidation validateInitializedWorkspace(String content, Path workspace)
            throws IOException, InterruptedException {
        CommandResult validation = executor.run(validateCommand(), workspace, validationTimeout);
        if (validation.timedOut()) {
            return failure(content, TerraformValidationFailureException.Category.VALIDATE_TIMEOUT,
                    "Terraform CLI validation timed out");
        }

        JsonNode diagnostics;
        try {
            diagnostics = objectMapper.readTree(validation.output());
        } catch (Exception exception) {
            return failure(content, TerraformValidationFailureException.Category.INTERNAL,
                    "Terraform CLI validation returned malformed diagnostics");
        }
        if (diagnostics == null || !diagnostics.path("valid").isBoolean()) {
            return failure(content, TerraformValidationFailureException.Category.INTERNAL,
                    "Terraform CLI validation returned malformed diagnostics");
        }
        if (validation.exitCode() != 0 || !diagnostics.path("valid").asBoolean()) {
            TerraformDiagnosticSummary summary = reduceDiagnostics(diagnostics);
            return new TerraformDraftValidation(false, content,
                    "VALIDATE_CONFIGURATION: generated Terraform failed Terraform CLI validation", summary);
        }
        return new TerraformDraftValidation(true, content, null);
    }

    private TerraformDiagnosticSummary reduceInitializationDiagnostics(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        TerraformDiagnosticSummary.DiagnosticClass diagnosticClass = List.of(
                        "invalid character",
                        "invalid expression",
                        "invalid escape sequence",
                        "invalid multi-line string",
                        "unclosed configuration block",
                        "argument or block definition required",
                        "missing newline after argument",
                        "missing key/value separator",
                        "invalid block definition")
                .stream()
                .anyMatch(normalized::contains)
                ? TerraformDiagnosticSummary.DiagnosticClass.CONFIGURATION_SYNTAX
                : TerraformDiagnosticSummary.DiagnosticClass.UNKNOWN;

        int errorCount = boundedLineCount(normalized, "error:");
        int warningCount = boundedLineCount(normalized, "warning:");
        return new TerraformDiagnosticSummary(
                List.of(diagnosticClass),
                Math.max(1, errorCount),
                warningCount
        );
    }

    private int boundedLineCount(String output, String prefix) {
        long count = output.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(prefix))
                .limit(TerraformDiagnosticSummary.MAX_COUNT)
                .count();
        return (int) count;
    }

    private TerraformDiagnosticSummary reduceDiagnostics(JsonNode envelope) {
        List<TerraformDiagnosticSummary.DiagnosticClass> classes = new ArrayList<>();
        JsonNode diagnostics = envelope.path("diagnostics");
        if (diagnostics.isArray()) {
            for (JsonNode diagnostic : diagnostics) {
                if ("error".equals(diagnostic.path("severity").asText().toLowerCase(Locale.ROOT))) {
                    classes.add(classify(diagnostic.path("summary").asText()));
                }
            }
        }
        return new TerraformDiagnosticSummary(classes,
                boundedJsonCount(envelope.path("error_count")),
                boundedJsonCount(envelope.path("warning_count")));
    }

    private int boundedJsonCount(JsonNode value) {
        if (!value.canConvertToInt()) return 0;
        return Math.max(0, Math.min(value.asInt(), TerraformDiagnosticSummary.MAX_COUNT));
    }

    private TerraformDiagnosticSummary.DiagnosticClass classify(String summary) {
        // Raw text is used only for these fixture-backed exact Terraform summary forms and is never returned.
        return switch (summary) {
            case "Missing required argument" -> TerraformDiagnosticSummary.DiagnosticClass.MISSING_REQUIRED_ARGUMENT;
            case "Unsupported argument", "Unsupported block type" ->
                    TerraformDiagnosticSummary.DiagnosticClass.UNSUPPORTED_ARGUMENT_OR_BLOCK;
            case "Reference to undeclared resource", "Reference to undeclared input variable",
                    "Reference to undeclared module" -> TerraformDiagnosticSummary.DiagnosticClass.UNDECLARED_REFERENCE;
            default -> TerraformDiagnosticSummary.DiagnosticClass.UNKNOWN;
        };
    }

    private Path createWorkspace() throws IOException {
        return tempRoot == null
                ? Files.createTempDirectory("terraformers-validate-")
                : Files.createTempDirectory(tempRoot, "terraformers-validate-");
    }

    private List<String> initCommand() {
        return List.of(
                terraformBinary,
                "init",
                "-backend=false",
                "-input=false",
                "-get=false",
                "-no-color",
                "-plugin-dir=" + pluginDir
        );
    }

    private List<String> validateCommand() {
        return List.of(terraformBinary, "validate", "-json", "-no-color");
    }

    private boolean deleteWorkspace(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return true;
        }
        try (Stream<Path> paths = Files.walk(workspace)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private TerraformDraftValidation invalid(String content, String reason) {
        return new TerraformDraftValidation(false, content, reason);
    }

    private TerraformDraftValidation failure(String content, TerraformValidationFailureException.Category category,
                                             String message) {
        return invalid(content, category.name() + ": " + message);
    }

    private TerraformDraftValidation failure(
            String content,
            TerraformValidationFailureException.Category category,
            String message,
            TerraformDiagnosticSummary diagnosticSummary
    ) {
        return new TerraformDraftValidation(false, content, category.name() + ": " + message, diagnosticSummary);
    }

    private boolean providerClosureFailure(String output) {
        String bounded = output == null ? "" : output.toLowerCase(java.util.Locale.ROOT);
        return bounded.contains("provider") && (bounded.contains("not found")
                || bounded.contains("unavailable") || bounded.contains("no available releases")
                || bounded.contains("does not match") || bounded.contains("failed to query available provider"));
    }

    interface CommandExecutor {
        CommandResult run(List<String> command, Path workingDirectory, Duration timeout)
                throws IOException, InterruptedException;
    }

    record CommandResult(int exitCode, boolean timedOut, String output) {
        CommandResult {
            output = output == null ? "" : output;
        }
    }

    static final class ProcessCommandExecutor implements CommandExecutor {

        @Override
        public CommandResult run(List<String> command, Path workingDirectory, Duration timeout)
                throws IOException, InterruptedException {
            Path outputFile = Files.createTempFile(workingDirectory, ".terraformers-cli-", ".log");
            try {
                ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command))
                        .directory(workingDirectory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(outputFile.toFile());
                Map<String, String> environment = builder.environment();
                environment.clear();
                environment.put("PATH", "/usr/local/bin:/usr/bin:/bin");
                environment.put("HOME", workingDirectory.toString());
                environment.put("TMPDIR", workingDirectory.toString());
                environment.put("TF_IN_AUTOMATION", "1");
                environment.put("TF_DATA_DIR", workingDirectory.resolve(".terraform-data").toString());
                environment.put("CHECKPOINT_DISABLE", "1");
                environment.put("AWS_EC2_METADATA_DISABLED", "true");

                Process process = builder.start();
                boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    terminate(process);
                    return new CommandResult(-1, true, readBounded(outputFile));
                }
                return new CommandResult(process.exitValue(), false, readBounded(outputFile));
            } finally {
                Files.deleteIfExists(outputFile);
            }
        }

        private void terminate(Process process) throws InterruptedException {
            process.descendants().forEach(handle -> {
                handle.destroy();
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            });
            process.destroy();
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        }

        private String readBounded(Path outputFile) throws IOException {
            try (InputStream input = Files.newInputStream(outputFile)) {
                return new String(input.readNBytes(MAX_CAPTURE_BYTES), StandardCharsets.UTF_8);
            }
        }
    }
}
