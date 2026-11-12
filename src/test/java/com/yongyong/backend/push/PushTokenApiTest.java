package com.yongyong.backend.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyong.backend.account.AccountService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.boot.test.context.SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@AutoConfigureMockMvc
@Transactional
class PushTokenApiTest {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate db;

    @Test
    void guardianCanRegisterEncryptedExpoTokenAndElderCannot() throws Exception {
        String tokenValue = "ExponentPushToken[abcdefghijklmnopqrstuv]";
        Jwt guardian = token("push-guardian");
        accounts.login(guardian, "GUARDIAN");
        mvc.perform(post("/api/v1/push-tokens").with(jwt().jwt(guardian)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenValue + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.registered").value(true));
        String encrypted = db.queryForObject("SELECT token_enc FROM push_tokens", String.class);
        assertThat(encrypted).doesNotContain(tokenValue);
        assertThat(db.queryForObject("SELECT platform FROM push_tokens", String.class)).isEqualTo("IOS");
        mvc.perform(delete("/api/v1/push-tokens").with(jwt().jwt(guardian)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + tokenValue + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.registered").value(false));
        assertThat(db.queryForObject("SELECT active FROM push_tokens", Boolean.class)).isFalse();

        Jwt elder = token("push-elder");
        accounts.login(elder, "ELDER");
        mvc.perform(post("/api/v1/push-tokens").with(jwt().jwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ExponentPushToken[abcdefghijklmnopqrstuv]\",\"platform\":\"IOS\"}"))
                .andExpect(status().isForbidden());
    }

    private static Jwt token(String subject) {
        return Jwt.withTokenValue("token").header("alg", "HS256")
                .issuer("https://auth.yongyongthon.example/phone").subject(subject)
                .audience(java.util.List.of("yongyongthon-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }
}
