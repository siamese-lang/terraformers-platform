package com.terraformers.modernization.analysis;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@Import({SynchronousAnalysisExecutorTestConfig.class, AnalysisJobControllerIntegrationTest.DiagnosticObjects.class})
class AnalysisJobControllerIntegrationTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private TerraformExecutableValidator executableValidator;

    @org.springframework.boot.test.mock.mockito.MockBean
    private StubAnalysisProvider analysisProvider;

    @org.junit.jupiter.api.BeforeEach
    void validCliByDefault() {
        org.mockito.Mockito.when(analysisProvider.analyze(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> new StubAnalysisProvider(objects).analyze(call.getArgument(0)));
        org.mockito.Mockito.when(executableValidator.validate(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> new TerraformDraftValidation(true, call.getArgument(0), "valid"));
    }

    @Test
    void failedValidationRetainsOwnerScopedCandidateAndSafeDiagnosticInsteadOfSuccess(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String failedHcl = """
                terraform {
                  required_providers {
                    aws = { source = "hashicorp/aws", version = "=5.100.0" }
                  }
                }
                # private-input-value-must-not-be-in-logs-or-summary
                resource "aws_lambda_function" "broken" {}
                """;
        org.mockito.Mockito.doReturn(new AnalysisResult("offline-injected", failedHcl, "", java.util.List.of(),
                        java.util.List.of(), java.util.List.of(), java.util.List.of()))
                .when(analysisProvider).analyze(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.when(executableValidator.validate(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> new TerraformDraftValidation(false, call.getArgument(0),
                        "VALIDATE_CONFIGURATION: Terraform CLI validation failed",
                        new TerraformDiagnosticSummary(java.util.List.of(
                                TerraformDiagnosticSummary.DiagnosticClass.MISSING_REQUIRED_ARGUMENT), 1, 0)));
        if (System.getProperty("pt8a.testTerraform") != null) {
            var real = new TerraformCliValidator(objectMapper, new TerraformCliValidator.ProcessCommandExecutor(),
                    System.getProperty("pt8a.testTerraform"), java.nio.file.Path.of(System.getProperty("pt8a.testPlugins")),
                    java.time.Duration.ofSeconds(60), java.time.Duration.ofSeconds(20), null);
            org.mockito.Mockito.when(executableValidator.validate(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(call -> real.validate(call.getArgument(0)));
        }
        JsonNode upload = createOwnedProjectAndSourceFile();
        String request = objectMapper.writeValueAsString(Map.of("projectId", upload.path("projectId").asLong(),
                "sourceFileId", upload.path("sourceFileId").asLong()));
        String jobId = objectMapper.readTree(mockMvc.perform(post("/api/analysis/jobs").with(testUserJwt())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(get("/api/analysis/jobs/{id}", jobId).with(testUserJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.resultFileId").value(nullValue()))
                .andExpect(jsonPath("$.resultPreview").value(nullValue()));
        mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", jobId).with(testUserJwt()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("private-input-value"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.failure.category").value("VALIDATE_CONFIGURATION"))
                .andExpect(jsonPath("$.candidates.final.sha256").isString())
                .andExpect(jsonPath("$.candidates.final.content").doesNotExist());
        MvcResult raw = mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", jobId)
                        .param("includeContent", "true").with(testUserJwt()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.facts.status").value("NOT_CAPTURED"))
                .andReturn();
        JsonNode evidence = objectMapper.readTree(raw.getResponse().getContentAsString());
        String candidate = evidence.path("candidates").path("final").path("content").asText();
        org.assertj.core.api.Assertions.assertThat(candidate).isEqualTo(failedHcl);
        org.assertj.core.api.Assertions.assertThat(evidence.path("candidates").path("validated").path("content").asText())
                .isEqualTo(failedHcl.strip());
        org.assertj.core.api.Assertions.assertThat(evidence.path("cliDiagnostics").path("errorCount").asInt()).isPositive();
        org.assertj.core.api.Assertions.assertThat(output.getAll()).doesNotContain("private-input-value", failedHcl);
        mockMvc.perform(get("/api/projects/{id}/terraform/main.tf", upload.path("projectId").asLong()).with(testUserJwt()))
                .andExpect(status().is4xxClientError());
        org.assertj.core.api.Assertions.assertThat(evidence.path("candidates").path("final").path("sha256").asText())
                .isEqualTo(AnalysisDiagnosticEvidence.sha(candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (System.getProperty("pt8a.testTerraform") != null) {
            // Repair the recovered synthetic candidate locally, without changing the failed job/result.
            String repaired = candidate.replace("resource \"aws_lambda_function\" \"broken\" {}", """
                    variable "function_name" { type = string }
                    variable "role_arn" { type = string }
                    variable "package_path" { type = string }
                    resource "aws_lambda_function" "broken" {
                      function_name = var.function_name
                      role          = var.role_arn
                      filename      = var.package_path
                      handler       = "index.handler"
                      runtime       = "python3.12"
                    }
                    """);
            var real = new TerraformCliValidator(objectMapper, new TerraformCliValidator.ProcessCommandExecutor(),
                    System.getProperty("pt8a.testTerraform"), java.nio.file.Path.of(System.getProperty("pt8a.testPlugins")),
                    java.time.Duration.ofSeconds(60), java.time.Duration.ofSeconds(20), null);
            org.assertj.core.api.Assertions.assertThat(real.validate(repaired).valid()).isTrue();
            org.assertj.core.api.Assertions.assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
        }
        var project = projects.findById(upload.path("projectId").asLong()).orElseThrow();
        project.setVisibility(com.terraformers.modernization.project.ProjectVisibility.PUBLIC); projects.saveAndFlush(project);
        mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", jobId).param("includeContent", "true")
                        .with(jwt().jwt(builder -> builder.subject("another-project-owner"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", jobId)).andExpect(status().isUnauthorized());
        var job = jobs.findById(jobId).orElseThrow();
        String objectKey = job.getDiagnosticBucket() + "/" + job.getDiagnosticKey();
        byte[] originalBytes = objects.values.get(objectKey);
        objects.values.put(objectKey, "corrupted-private-evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", jobId).param("includeContent", "true").with(testUserJwt()))
                .andExpect(jsonPath("$.status").value("DIAGNOSTIC_EVIDENCE_INCOMPLETE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("corrupted-private"))));
        objects.values.put(objectKey, originalBytes);
        org.assertj.core.api.Assertions.assertThat(job.getDiagnosticExpiresAt()).isBefore(java.time.Instant.now().plusSeconds(7 * 86400 + 1));
        // Fence the real background cleanup before publishing expiry, so this deletion-failure
        // assertion cannot race a successful scheduler sweep.
        objects.failRemoves = true;
        job.setDiagnosticExpiresAt(java.time.Instant.now().minusSeconds(1)); jobs.saveAndFlush(job);
        mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", jobId).with(testUserJwt()))
                .andExpect(jsonPath("$.status").value("DIAGNOSTIC_EVIDENCE_INCOMPLETE"));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> diagnosticStorage.expire(jobId, job.getClaimGeneration()))
                    .isInstanceOf(IllegalStateException.class);
            org.assertj.core.api.Assertions.assertThat(jobs.findExpiredDiagnostics(java.time.Instant.now(),
                    org.springframework.data.domain.PageRequest.of(0, 20))).extracting(AnalysisJobEntity::getId).contains(jobId);
        } finally { objects.failRemoves = false; }
        diagnosticStorage.expire(jobId, job.getClaimGeneration());
        org.assertj.core.api.Assertions.assertThat(objects.values).doesNotContainKey(job.getDiagnosticBucket() + "/" + job.getDiagnosticKey());
        org.assertj.core.api.Assertions.assertThat(jobs.findById(jobId).orElseThrow().getDiagnosticStatus()).isEqualTo("EXPIRED");

    }


    @Test
    void diagnosticWriteFailureIsExplicitAndDoesNotRegisterFailedHclAsResult() throws Exception {
        objects.failWrites = true;
        try {
            org.mockito.Mockito.when(executableValidator.validate(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(call -> new TerraformDraftValidation(false, call.getArgument(0), "INIT_TIMEOUT: initialization timed out"));
            JsonNode upload = createOwnedProjectAndSourceFile();
            var latest = jobs.findFirstByProjectIdOrderByCreatedAtDesc(upload.path("projectId").asLong()).orElseThrow();
            mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", latest.getId()).with(testUserJwt()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DIAGNOSTIC_EVIDENCE_INCOMPLETE"));
            org.assertj.core.api.Assertions.assertThat(latest.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            org.assertj.core.api.Assertions.assertThat(latest.getResultFileId()).isNull();
            org.assertj.core.api.Assertions.assertThat(jobs.findById(latest.getId()).orElseThrow().getDiagnosticStatus()).isEqualTo("INCOMPLETE");
        } finally { objects.failWrites = false; }
    }

    @Test
    void lateDiagnosticWriterCannotRestoreExpiredEvidenceOrLoseCleanupAccountability() throws Exception {
        objects.beforeDiagnosticWrite = request -> {
            String id = request.key().split("/")[2];
            var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            var expired = transaction.execute(tx -> {
                var current = jobs.findById(id).orElseThrow();
                current.setDiagnosticExpiresAt(java.time.Instant.now().minusSeconds(1));
                return jobs.saveAndFlush(current);
            });
            diagnosticStorage.expire(id, expired.getClaimGeneration()); // Deletes before the blocked write finishes.
        };
        try {
            JsonNode upload = createOwnedProjectAndSourceFile();
            var job = jobs.findFirstByProjectIdOrderByCreatedAtDesc(upload.path("projectId").asLong()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.SUCCEEDED);
            org.assertj.core.api.Assertions.assertThat(job.getDiagnosticStatus()).isEqualTo("EXPIRED");
            org.assertj.core.api.Assertions.assertThat(objects.values).doesNotContainKey(job.getDiagnosticBucket() + "/" + job.getDiagnosticKey());
            mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", job.getId()).with(testUserJwt()))
                    .andExpect(jsonPath("$.status").value("DIAGNOSTIC_EVIDENCE_INCOMPLETE"));
        } finally { objects.beforeDiagnosticWrite = null; }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class DiagnosticObjects {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        MemoryObjects diagnosticObjects() { return new MemoryObjects(); }
    }
    static class MemoryObjects implements com.terraformers.modernization.storage.ObjectWriter,
            com.terraformers.modernization.storage.ObjectReader, com.terraformers.modernization.storage.ObjectRemover {
        final java.util.Map<String, byte[]> values = new java.util.concurrent.ConcurrentHashMap<>();
        boolean failWrites;
        boolean failRemoves;
        java.util.function.Consumer<com.terraformers.modernization.storage.ObjectWriteRequest> beforeDiagnosticWrite;
        public com.terraformers.modernization.storage.ObjectWriteResult writeText(
                com.terraformers.modernization.storage.ObjectWriteRequest request) {
            if (failWrites && request.key().startsWith("analysis-diagnostics/")) throw new IllegalStateException("sensitive storage message");
            if (request.key().startsWith("analysis-diagnostics/") && beforeDiagnosticWrite != null) beforeDiagnosticWrite.accept(request);
            values.put(request.bucket() + "/" + request.key(), request.content().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new com.terraformers.modernization.storage.ObjectWriteResult("memory", true, request.bucket(), request.key(), "etag");
        }
        public com.terraformers.modernization.storage.ObjectWriteResult writeBytes(
                com.terraformers.modernization.storage.ObjectBinaryWriteRequest request) {
            values.put(request.bucket() + "/" + request.key(), request.bytes());
            return new com.terraformers.modernization.storage.ObjectWriteResult("memory", true, request.bucket(), request.key(), "etag");
        }
        public void remove(com.terraformers.modernization.storage.ObjectReference reference) {
            if (failRemoves && reference.key().startsWith("analysis-diagnostics/")) throw new IllegalStateException("offline injected delete failure");
            values.remove(reference.bucket() + "/" + reference.key());
        }
        public com.terraformers.modernization.storage.ObjectMetadata readMetadata(
                com.terraformers.modernization.storage.ObjectReference reference) {
            byte[] bytes = values.getOrDefault(reference.bucket() + "/" + reference.key(), new byte[0]);
            return new com.terraformers.modernization.storage.ObjectMetadata(reference.bucket(), reference.key(), "application/json", bytes.length, "etag");
        }
        public com.terraformers.modernization.storage.ObjectContent readContent(
                com.terraformers.modernization.storage.ObjectReference reference) {
            byte[] bytes = values.get(reference.bucket() + "/" + reference.key());
            if (bytes == null) throw new IllegalStateException("object unavailable");
            return new com.terraformers.modernization.storage.ObjectContent(readMetadata(reference), bytes);
        }
    }

    @Autowired private MemoryObjects objects;
    @Autowired private AnalysisJobRepository jobs;
    @Autowired private com.terraformers.modernization.projectcore.OwnedProjectRepository projects;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private AnalysisDiagnosticStorage diagnosticStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAnalysisJobReturnsSucceededStateAndResultArtifact() throws Exception {
        JsonNode upload = createOwnedProjectAndSourceFile();
        long projectId = upload.path("projectId").asLong();
        long sourceFileId = upload.path("sourceFileId").asLong();

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "projectId", projectId,
                "sourceFileId", sourceFileId,
                "correlationId", "integration-smoke"
        ));

        MvcResult createResult = mockMvc.perform(post("/api/analysis/jobs")
                        .with(testUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/analysis/jobs/")))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.sourceFileId").value(sourceFileId))
                .andExpect(jsonPath("$.resultFileId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resultObjectKey").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resultPreview").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.failureReason").value(nullValue()))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String jobId = created.path("id").asText();

        mockMvc.perform(get("/api/analysis/jobs/{id}/diagnostics", jobId).with(testUserJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.stages.result_finalization.status").value("CAPTURED"));
        mockMvc.perform(get("/api/analysis/jobs/{id}", jobId).with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.sourceFileId").value(sourceFileId))
                .andExpect(jsonPath("$.resultFileId").isNumber())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultObjectKey", not(nullValue())))
                .andExpect(jsonPath("$.resultPreview", not(nullValue())))
                .andExpect(jsonPath("$.quality").value(nullValue()));
    }

    private JsonNode createOwnedProjectAndSourceFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "integration-architecture.png",
                "image/png",
                "fake image bytes".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/upload")
                        .file(file)
                        .param("projectName", "Integration Project")
                        .with(testUserJwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").isNumber())
                .andExpect(jsonPath("$.sourceFileId").isNumber())
                .andReturn();
        return objectMapper.readTree(uploadResult.getResponse().getContentAsString());
    }

    private RequestPostProcessor testUserJwt() {
        return jwt().jwt(builder -> builder
                .subject("analysis-controller-integration-user")
                .claim("email", "analysis-controller@example.com")
                .claim("name", "Analysis Controller User"));
    }
}
