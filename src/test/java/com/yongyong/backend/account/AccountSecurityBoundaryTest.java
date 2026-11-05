package com.yongyong.backend.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.auth.jwt-secret=", "app.data-key="})
@AutoConfigureMockMvc
class AccountSecurityBoundaryTest {
    @Autowired MockMvc mvc;

    @Test
    void missingSigningKeyClosesAccountEndpointsWithoutBlockingOtherModules() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/unrelated-module-test-path")).andExpect(status().isNotFound());
    }
}
