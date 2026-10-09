package com.example.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class HealthIntegrationTest extends IntegrationTestSupport {

    @Test
    void healthIsPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthDoesNotExposeInternalDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void livenessAndReadinessProbesArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherManagementEndpointsAreNotPublic() throws Exception {
        for (String path : new String[] {"/actuator", "/actuator/env", "/actuator/metrics", "/actuator/beans", "/actuator/info"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void otherManagementEndpointsAreNotExposedEvenWithToken() throws Exception {
        String token = tokenFor(saveUser("Usuario", "usuario@example.com"));

        for (String path : new String[] {"/actuator/env", "/actuator/metrics", "/actuator/beans"}) {
            mockMvc.perform(authorized(get(path), token)).andExpect(status().isNotFound());
        }
    }

    @Test
    void businessEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/losses")).andExpect(status().isUnauthorized());
    }
}
