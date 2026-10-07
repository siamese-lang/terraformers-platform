package com.terraformers.modernization.project;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisJobRepository;
import com.terraformers.modernization.analysis.AnalysisJobEntity;
import com.terraformers.modernization.analysis.AnalysisJobStatus;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.terraformers.modernization.analysis.SynchronousAnalysisExecutorTestConfig;
import com.terraformers.modernization.collaboration.BoardRepository;
import com.terraformers.modernization.collaboration.CommentRepository;
import com.terraformers.modernization.identity.UserRepository;
import com.terraformers.modernization.identity.UserEntity;
import com.terraformers.modernization.projectcore.OwnedProjectRepository;
import com.terraformers.modernization.projectcore.ProjectFileRepository;
import com.terraformers.modernization.projectcore.ProjectFileEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SynchronousAnalysisExecutorTestConfig.class)
@ActiveProfiles("test")
class ProjectMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private ProjectFileRepository projectFileRepository;

    @Autowired
    private OwnedProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanState() {
        commentRepository.deleteAll();
        boardRepository.deleteAll();
        analysisJobRepository.deleteAll();
        projectFileRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(EvidenceQualityAssessment.QualityStatus.class)
    void metadataAndPublicTreeExposeTheSameJobQualityAndTiming(EvidenceQualityAssessment.QualityStatus qualityStatus) throws Exception {
        Long projectId = upload("Trust projection.png");
        AnalysisJobEntity job = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElseThrow();
        var reasons = qualityStatus == EvidenceQualityAssessment.QualityStatus.DEGRADED
                ? List.of(EvidenceQualityAssessment.Reason.CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING)
                : List.<EvidenceQualityAssessment.Reason>of();
        job.setQualityAssessment(new EvidenceQualityAssessment(EvidenceQualityAssessment.CONTRACT_VERSION,
                EvidenceQualityAssessment.TechnicalStatus.PASS, EvidenceQualityAssessment.KnowledgeStatus.COMPLETE,
                qualityStatus, EvidenceQualityAssessment.ProjectDecisionStatus.UNKNOWN,
                EvidenceQualityAssessment.RuntimeQualityBoundary.CONDITIONAL_ON_EXTRACTED_FACTS,
                reasons, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        analysisJobRepository.saveAndFlush(job);
        JsonNode jobResponse = objectMapper.readTree(mockMvc.perform(get("/api/analysis/jobs/" + job.getId()).with(testUserJwt()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.quality.qualityStatus").value(qualityStatus.name()))
                .andExpect(jsonPath("$.quality.technicalStatus").value("PASS"))
                .andExpect(jsonPath("$.analysisTiming.acceptedToTerminalMs").isNumber())
                .andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(detail.get("quality")).isEqualTo(jobResponse.get("quality"));
        org.assertj.core.api.Assertions.assertThat(detail.get("analysisTiming")).isEqualTo(jobResponse.get("timing"));
        mockMvc.perform(get("/api/projects").with(testUserJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quality.qualityStatus").value(qualityStatus.name()));
        // Existing private/public authorization is reused; adding metadata does not open a route.
        mockMvc.perform(get("/api/projects/" + projectId)).andExpect(status().isForbidden());
        publishProject(projectId);
        JsonNode tree = objectMapper.readTree(mockMvc.perform(get("/api/project-tree/" + projectId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(tree.get("quality")).isEqualTo(detail.get("quality"));
        org.assertj.core.api.Assertions.assertThat(tree.get("analysisTiming")).isEqualTo(detail.get("analysisTiming"));
    }

    @Test
    void legacyCompletedProjectDoesNotInventQualityOrLatencyFromLaterMetadataUpdate() throws Exception {
        Long projectId = upload("Legacy trust.png");
        AnalysisJobEntity job = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(job, "qualityContractVersion", null);
        job.setTerminalAt(null);
        job.setAnalysisWarnings("later metadata edit");
        analysisJobRepository.saveAndFlush(job);
        mockMvc.perform(get("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analysisStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.quality").doesNotExist())
                .andExpect(jsonPath("$.analysisTiming.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.analysisTiming.terminalAt").doesNotExist())
                .andExpect(jsonPath("$.analysisTiming.acceptedToTerminalMs").doesNotExist());
    }

    @Test
    void uploadCreatesQueryableCanonicalProjectMetadata() throws Exception {
        Long projectId = upload("Project Tree Diagram.png");

        mockMvc.perform(get("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.displayName").value("Project Tree Diagram"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.latestAnalysisJobId").isNotEmpty())
                .andExpect(jsonPath("$.latestResultFileId").isNumber())
                .andExpect(jsonPath("$.latestResultObjectKey").isNotEmpty())
                .andExpect(jsonPath("$.sourceFileId").isNumber())
                .andExpect(jsonPath("$.sourceBucket").value("example-bucket"))
                .andExpect(jsonPath("$.sourceKey").value(startsWith("browser-uploads/" + projectId + "/")))
                .andExpect(jsonPath("$.sourceStorageProvider").value("metadata-only"))
                .andExpect(jsonPath("$.sourceBinaryPersisted").value(false))
                .andExpect(jsonPath("$.originalFilename").value("Project Tree Diagram.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.uploadSizeBytes").value(16));
    }

    @Test
    void metadataOnlySourceObjectReadReturnsConflictForOwner() throws Exception {
        Long projectId = upload("Project Source.png");

        mockMvc.perform(get("/api/projects/" + projectId + "/source-object").with(testUserJwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void detailAndTerraformRemainBoundToLatestJobFilesWhenNewerPhysicalFilesExist() throws Exception {
        Long projectId = upload("Job Bound.png");
        AnalysisJobEntity job = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElseThrow();
        Long jobSourceFileId = job.getSourceFileId();
        Long jobResultFileId = job.getResultFileId();
        ProjectFileEntity source = projectFileRepository.findById(jobSourceFileId).orElseThrow();
        ProjectFileEntity result = projectFileRepository.findById(jobResultFileId).orElseThrow();
        projectFileRepository.save(copyFile(source, "source/newer.png", "newer.png", "ARCHITECTURE_IMAGE"));
        projectFileRepository.save(copyFile(result, "terraform/newer.tf", "newer.tf", "GENERATED_TERRAFORM"));

        mockMvc.perform(get("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceFileId").value(jobSourceFileId))
                .andExpect(jsonPath("$.resultFileId").value(jobResultFileId));
        mockMvc.perform(get("/api/projects/" + projectId + "/terraform/main.tf").with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(jobResultFileId));
    }

    @Test
    void failedLatestJobDoesNotReturnPreviousTerraform() throws Exception {
        Long projectId = upload("Failed Job.png");
        AnalysisJobEntity job = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElseThrow();
        job.setStatus(AnalysisJobStatus.FAILED);
        job.setResultFileId(null);
        job.setFailureReason("analysis failed");
        analysisJobRepository.save(job);

        mockMvc.perform(get("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.resultFileId").doesNotExist())
                .andExpect(jsonPath("$.failureReason").value("analysis failed"));
        mockMvc.perform(get("/api/projects/" + projectId + "/terraform/main.tf").with(testUserJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void visibilityCanBeUpdatedAndListedAsPublic() throws Exception {
        Long projectId = upload("Public Project.png");
        publishProject(projectId);

        mockMvc.perform(get("/api/projects/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"));

        mockMvc.perform(get("/api/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    @Test
    void publicProjectsCompatibilityListsOnlyPublicProjects() throws Exception {
        upload("Private Design.png");
        Long publicProjectId = upload("Shared Architecture.png");
        publishProject(publicProjectId);

        mockMvc.perform(get("/api/public-projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectId").value(publicProjectId))
                .andExpect(jsonPath("$[0].id").value(publicProjectId))
                .andExpect(jsonPath("$[0].projectName").value("Shared Architecture"))
                .andExpect(jsonPath("$[0].name").value("Shared Architecture"))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[0].isPrivate").value(false))
                .andExpect(jsonPath("$[0].sourceBucket").value("example-bucket"))
                .andExpect(jsonPath("$[0].sourceKey").value(startsWith("browser-uploads/" + publicProjectId + "/")))
                .andExpect(jsonPath("$[0].sourceStorageProvider").value("metadata-only"))
                .andExpect(jsonPath("$[0].sourceBinaryPersisted").value(false))
                .andExpect(jsonPath("$[0].imageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].latestResultFileId").isNumber())
                .andExpect(jsonPath("$[0].projectTreeApiPath").value("/api/project-tree/" + publicProjectId))
                .andExpect(jsonPath("$[0].terraformDraftApiPath")
                        .value("/api/projects/" + publicProjectId + "/terraform/main.tf"));
    }

    @Test
    void publicProjectImageUrlIsReturnedOnlyForPersistedSource() throws Exception {
        Long projectId = upload("Persisted.png");
        AnalysisJobEntity job = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElseThrow();
        ProjectFileEntity source = projectFileRepository.findById(job.getSourceFileId()).orElseThrow();
        source.setBinaryPersisted(true);
        projectFileRepository.save(source);
        publishProject(projectId);
        mockMvc.perform(get("/api/public-projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].imageUrl").value("/api/projects/" + projectId + "/source-image"));
    }

    @Test
    void ownedProjectListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownProjectReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/projects/999999").with(testUserJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanDeleteProjectAndDeletedProjectIsExcludedFromOwnedList() throws Exception {
        Long projectId = upload("Delete Me.png");

        mockMvc.perform(delete("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects").with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonOwnerCannotDeletePrivateProject() throws Exception {
        Long projectId = upload("Private Delete Target.png");

        mockMvc.perform(delete("/api/projects/" + projectId).with(otherUserJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/projects/" + projectId).with(testUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/projects/{id}, 200",
            "/api/project-tree/{id}, 200",
            "/api/projects/{id}/terraform/main.tf, 200",
            "/api/projects/{id}/source-object, 409",
            "/api/projects/{id}/source-image, 409"
    })
    void privatePublicReadMatrixAndRevocationFollowProjectOwnership(String route, int allowedStatus) throws Exception {
        Long projectId = upload("Access Matrix.png");
        String path = route.replace("{id}", projectId.toString()) + "?audit=true";
        mockMvc.perform(get(path).with(testUserJwt())).andExpect(status().is(allowedStatus));
        mockMvc.perform(get(path).with(otherUserJwt())).andExpect(status().isForbidden());
        mockMvc.perform(get(path)).andExpect(status().isForbidden());

        publishProject(projectId);
        mockMvc.perform(get(path)).andExpect(status().is(allowedStatus));
        mockMvc.perform(get(path).with(otherUserJwt())).andExpect(status().is(allowedStatus));
        mockMvc.perform(patch("/api/projects/" + projectId + "/visibility").with(testUserJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(path)).andExpect(status().isForbidden());
        mockMvc.perform(get(path).with(otherUserJwt())).andExpect(status().isForbidden());
    }

    @Test
    void jobReadRequiresAuthenticationAndFollowsItsProjectVisibility() throws Exception {
        Long projectId = upload("Job Access.png");
        AnalysisJobEntity job = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElseThrow();
        String path = "/api/analysis/jobs/" + job.getId();
        mockMvc.perform(get(path).with(testUserJwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.sourceFileId").value(job.getSourceFileId()))
                .andExpect(jsonPath("$.resultFileId").value(job.getResultFileId()));
        mockMvc.perform(get(path).with(otherUserJwt())).andExpect(status().isForbidden());
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        publishProject(projectId);
        mockMvc.perform(get(path).with(otherUserJwt())).andExpect(status().isOk());
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deniedPrivateOrPublicMutationsDoNotChangeDataOrAcceptJobs(boolean publicProject) throws Exception {
        Long projectId = upload("Mutation Access.png");
        if (publicProject) {
            publishProject(projectId);
        }
        AnalysisJobEntity job = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElseThrow();
        String originalDraft = projectFileRepository.findById(job.getResultFileId()).orElseThrow().getInlineContent();
        long jobCount = analysisJobRepository.count();
        long fileCount = projectFileRepository.count();
        String jobRequest = "{\"projectId\":" + projectId + ",\"sourceFileId\":" + job.getSourceFileId() + "}";
        String newVisibility = publicProject ? "PRIVATE" : "PUBLIC";

        for (boolean anonymous : new boolean[] {true, false}) {
            RequestPostProcessor identity = anonymous ? request -> request : otherUserJwt();
            int deniedStatus = anonymous ? 401 : 403;
            mockMvc.perform(patch("/api/projects/" + projectId + "/visibility").with(identity)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"" + newVisibility + "\"}"))
                    .andExpect(status().is(deniedStatus));
            mockMvc.perform(put("/api/projects/" + projectId + "/terraform/main.tf").with(identity)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"unauthorized edit\"}"))
                    .andExpect(status().is(deniedStatus));
            mockMvc.perform(delete("/api/projects/" + projectId).with(identity))
                    .andExpect(status().is(deniedStatus));
            mockMvc.perform(post("/api/analysis/jobs").with(identity)
                            .contentType(MediaType.APPLICATION_JSON).content(jobRequest))
                    .andExpect(status().is(deniedStatus));
        }
        assertThat(analysisJobRepository.count()).isEqualTo(jobCount);
        assertThat(projectFileRepository.count()).isEqualTo(fileCount);
        assertThat(projectFileRepository.findById(job.getResultFileId()).orElseThrow().getInlineContent()).isEqualTo(originalDraft);
        var project = projectRepository.findById(projectId).orElseThrow();
        assertThat(project.getDeletedAt()).isNull();
        assertThat(project.getVisibility()).isEqualTo(publicProject ? ProjectVisibility.PUBLIC : ProjectVisibility.PRIVATE);

        mockMvc.perform(put("/api/projects/" + projectId + "/terraform/main.tf").with(testUserJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"# owner draft\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("# owner draft"));
        mockMvc.perform(patch("/api/projects/" + projectId + "/visibility").with(testUserJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visibility\":\"" + newVisibility + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/analysis/jobs").with(testUserJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(jobRequest)).andExpect(status().isCreated());
        assertThat(analysisJobRepository.count()).isEqualTo(jobCount + 1);
        mockMvc.perform(delete("/api/projects/" + projectId).with(testUserJwt())).andExpect(status().isNoContent());
    }

    @Test
    void jobCreationRejectsForeignOrDeletedSourceBeforeAcceptingAJob() throws Exception {
        Long ownedProject = upload("Own Source.png");
        Long foreignProject = upload("Foreign Source.png");
        var foreign = projectRepository.findById(foreignProject).orElseThrow();
        UserEntity other = userRepository.save(otherUser());
        foreign.setOwner(other);
        projectRepository.saveAndFlush(foreign);
        AnalysisJobEntity ownJob = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(ownedProject).orElseThrow();
        AnalysisJobEntity foreignJob = analysisJobRepository.findFirstByProjectIdOrderByCreatedAtDesc(foreignProject).orElseThrow();
        long count = analysisJobRepository.count();
        mockMvc.perform(post("/api/analysis/jobs").with(testUserJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + ownedProject + ",\"sourceFileId\":" + foreignJob.getSourceFileId() + "}"))
                .andExpect(status().isBadRequest());
        ProjectFileEntity source = projectFileRepository.findById(ownJob.getSourceFileId()).orElseThrow();
        source.setDeletedAt(java.time.Instant.now());
        projectFileRepository.saveAndFlush(source);
        mockMvc.perform(post("/api/analysis/jobs").with(testUserJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + ownedProject + ",\"sourceFileId\":" + source.getFileId() + "}"))
                .andExpect(status().isNotFound());
        assertThat(analysisJobRepository.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @CsvSource({"/api/projects/{id}/comments", "/api/getProjectComments/{id}"})
    void commentsReadOnlyPublicAndAuthenticatedNonOwnerMayDiscussButNotMutateProject(String route) throws Exception {
        Long projectId = upload("Discussion Access.png");
        String path = route.replace("{id}", projectId.toString());
        mockMvc.perform(get(path)).andExpect(status().isForbidden());
        mockMvc.perform(get(path).with(testUserJwt())).andExpect(status().isForbidden());
        mockMvc.perform(get(path).with(otherUserJwt())).andExpect(status().isForbidden());
        publishProject(projectId);
        mockMvc.perform(post("/api/projects/" + projectId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"discussion\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/projects/" + projectId + "/comments").with(otherUserJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"discussion\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorDisplayName").value("Other Metadata User"));
        mockMvc.perform(post("/api/addProjectComment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + projectId + ",\"content\":\"discussion\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/addProjectComment").with(otherUserJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + projectId + ",\"content\":\"compatibility discussion\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorDisplayName").value("Other Metadata User"));
        mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    private UserEntity otherUser() {
        UserEntity user = new UserEntity();
        user.setExternalIdentity("cognito", "metadata-other-user");
        user.setEmail("other-metadata@example.com");
        user.setDisplayName("Other Metadata User");
        return user;
    }

    private Long upload(String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "image/png",
                "fake image bytes".getBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/upload")
                        .file(file)
                        .param("projectName", filename.replace(".png", ""))
                        .with(testUserJwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").isNumber())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("projectId").asLong();
    }

    private void publishProject(Long projectId) throws Exception {
        mockMvc.perform(patch("/api/projects/" + projectId + "/visibility")
                        .with(testUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
    }

    private ProjectFileEntity copyFile(ProjectFileEntity original, String path, String name, String type) {
        ProjectFileEntity copy = new ProjectFileEntity();
        copy.setProject(original.getProject());
        copy.setNodeType("FILE");
        copy.setFileType(type);
        copy.setPath(path);
        copy.setSortOrder(200);
        copy.setOriginalFilename(name);
        copy.setS3Bucket(original.getS3Bucket());
        copy.setS3Key(original.getS3Key() + ".newer");
        copy.setStorageProvider(original.getStorageProvider());
        copy.setBinaryPersisted(original.isBinaryPersisted());
        copy.setContentType(original.getContentType());
        copy.setInlineContent("newer physical artifact");
        return copy;
    }

    private RequestPostProcessor testUserJwt() {
        return jwt().jwt(builder -> builder
                .subject("metadata-test-user")
                .claim("email", "metadata@example.com")
                .claim("name", "Metadata User"));
    }

    private RequestPostProcessor otherUserJwt() {
        return jwt().jwt(builder -> builder
                .subject("metadata-other-user")
                .claim("email", "other-metadata@example.com")
                .claim("name", "Other Metadata User"));
    }
}
