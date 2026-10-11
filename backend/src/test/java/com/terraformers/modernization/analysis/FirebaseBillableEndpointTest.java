package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.terraformers.modernization.identity.UserRepository;
import com.terraformers.modernization.identity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "terraformers.security.jwt.provider=firebase",
        "terraformers.security.jwt.issuer-uri=https://securetoken.google.com/terraformers-test",
        "terraformers.security.jwt.firebase.project-id=terraformers-test",
        "terraformers.security.jwt.firebase.billable-uids=uid-allowed"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SynchronousAnalysisExecutorTestConfig.class)
class FirebaseBillableEndpointTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;

    @Test
    void uploadIsUnauthenticatedOrUnapprovedByDefaultButVerifiedEnrolledUserCanSubmitOnce() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isUnauthorized());
        mockMvc.perform(upload().with(jwt().jwt(token -> token.subject("uid-other")
                .claim("email_verified", true)))).andExpect(status().isForbidden());
        mockMvc.perform(upload().with(jwt().jwt(token -> token.subject("uid-allowed")
                .claim("email_verified", false)))).andExpect(status().isForbidden());
        mockMvc.perform(upload().with(jwt().jwt(token -> token.subject("uid-allowed")
                .claim("email_verified", true).claim("role", "ADMIN"))))
                .andExpect(status().isCreated());
        assertThat(users.findByExternalIdentityProviderAndExternalIdentitySubject("firebase", "uid-allowed")
                .orElseThrow().getRole()).isEqualTo(UserRole.USER);
        mockMvc.perform(upload().with(jwt().jwt(token -> token.subject("uid-allowed")
                .claim("email_verified", true)))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload() {
        return multipart("/api/upload")
                .file(new MockMultipartFile("file", "diagram.png", "image/png", new byte[] {1, 2, 3}))
                .param("projectName", "Firebase project");
    }
}
