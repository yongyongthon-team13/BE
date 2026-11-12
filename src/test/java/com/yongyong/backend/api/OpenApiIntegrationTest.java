package com.yongyong.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.boot.test.context.SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:account-test.properties")
class OpenApiIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void swaggerUiAndOpenApiSpecArePublicAndDescribeJwtSecurity() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mvc.perform(get("/v3/api-docs/assigned-api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("길벗 API"))
                .andExpect(jsonPath("$.paths['/api/v1/route-plans/search'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/places'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/appointments/{appointmentId}/transport-requests'].post").exists());
    }
}
