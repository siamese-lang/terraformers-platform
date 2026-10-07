package com.terraformers.modernization.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JwtResourceServerSecurityConfigTest.UnlistedController.class)
class JwtResourceServerSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedJwtCanReachProtectedRoute() throws Exception {
        mockMvc.perform(get("/api/projects").with(jwt().jwt(token -> token
                        .claim("sub", "security-test-subject")
                        .claim("cognito:username", "security-test-user"))))
                .andExpect(status().isOk());
    }

    @Test
    void publicHealthAndInfoRoutesDoNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/security-regression/unlisted",
            "POST, /api/security-regression/unlisted",
            "GET, /api/projects/export",
            "GET, /api/projects/42/unlisted",
            "GET, /internal/runtime/unlisted",
            "GET, /actuator/unlisted"
    })
    void anUnlistedHandlerRequiresAuthentication(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void existingMonitoringAndReadinessGetsRemainPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mockMvc.perform(get("/internal/runtime/required-config")).andExpect(status().isOk());
    }

    @Test
    void malformedBearerIsRejectedEvenOnAPublicRoute() throws Exception {
        mockMvc.perform(get("/actuator/info").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optionsDoesNotGrantAnUnconfiguredCrossOriginRequest() throws Exception {
        mockMvc.perform(options("/api/security-regression/unlisted")).andExpect(status().isOk());
        mockMvc.perform(options("/api/upload")
                        .header("Origin", "https://unconfigured.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isForbidden());
    }

    // Emulate a future handler omitted from the public/protected lists. No production route is added.
    @RestController
    static class UnlistedController {
        @RequestMapping(value = {"/api/security-regression/unlisted", "/api/projects/export",
                "/api/projects/42/unlisted", "/internal/runtime/unlisted", "/actuator/unlisted"},
                method = {RequestMethod.GET, RequestMethod.POST})
        String unlisted() {
            return "unlisted";
        }
    }
}
