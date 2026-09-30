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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TerraformCliValidator implements TerraformExecutableValidator {

    static final String DEFAULT_TERRAFORM_BINARY = "/usr/local/bin/terraform";
    static final Path DEFAULT_PLUGIN_DIR = Path.of("/opt/terraform-plugins");
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_CAPTURE_BYTES = 32 * 1024;

    private final ObjectMapper objectMapper;
    private final CommandExecutor executor;
    private final String terraformBinary;
    private final Path pluginDir;
    private final Duration timeout;
    private final Path tempRoot;

    @Autowired
    public TerraformCliValidator(ObjectMapper objectMapper) {
        this(objectMapper, new ProcessCommandExecutor(), DEFAULT_TERRAFORM_BINARY,
                DEFAULT_PLUGIN_DIR, DEFAULT_TIMEOUT, null);
    }

    TerraformCliValidator(
            ObjectMapper objectMapper,
            CommandExecutor executor,
            String terraformBinary,
            Path pluginDir,
            Duration timeout,
            Path tempRoot
    ) {
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.terraformBinary = terraformBinary;
        this.pluginDir = pluginDir;
        this.timeout = timeout;
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

            CommandResult init = executor.run(initCommand(), workspace, timeout);
            if (init.timedOut()) {
                outcome = invalid(content, "Terraform CLI initialization timed out");
            } else if (init.exitCode() != 0) {
                outcome = invalid(content, "generated Terraform failed offline Terraform initialization");
            } else {
                outcome = validateInitializedWorkspace(content, workspace);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            outcome = invalid(content, "Terraform CLI validation was interrupted");
        } catch (IOException | RuntimeException exception) {
            outcome = invalid(content, "Terraform CLI validation could not be completed");
        }

        if (!deleteWorkspace(workspace)) {
            return invalid(content, "Terraform CLI validation workspace cleanup failed");
        }
        return outcome;
    }

    private TerraformDraftValidation validateInitializedWorkspace(String content, Path workspace)
            throws IOException, InterruptedException {
        CommandResult validation = executor.run(validateCommand(), workspace, timeout);
        if (validation.timedOut()) {
            return invalid(content, "Terraform CLI validation timed out");
        }

        JsonNode diagnostics;
        try {
            diagnostics = objectMapper.readTree(validation.output());
        } catch (Exception exception) {
            return invalid(content, "Terraform CLI validation returned malformed diagnostics");
        }
        if (diagnostics == null || !diagnostics.path("valid").isBoolean()) {
            return invalid(content, "Terraform CLI validation returned malformed diagnostics");
        }
        if (validation.exitCode() != 0 || !diagnostics.path("valid").asBoolean()) {
            return invalid(content, "generated Terraform failed Terraform CLI validation");
        }
        return new TerraformDraftValidation(true, content, null);
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
                    process.destroy();
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(1, TimeUnit.SECONDS);
                    }
                    return new CommandResult(-1, true, readBounded(outputFile));
                }
                return new CommandResult(process.exitValue(), false, readBounded(outputFile));
            } finally {
                Files.deleteIfExists(outputFile);
            }
        }

        private String readBounded(Path outputFile) throws IOException {
            try (InputStream input = Files.newInputStream(outputFile)) {
                return new String(input.readNBytes(MAX_CAPTURE_BYTES), StandardCharsets.UTF_8);
            }
        }
    }
}
