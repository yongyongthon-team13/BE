package com.yongyong.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:account-test.properties",
        properties = {"app.auth.demo-phones=010-0000-0001", "app.auth.demo-code=135790"})
@Transactional
class PhoneAuthServiceTest {
    @Autowired PhoneAuthService auth;
    @Autowired AccountService accounts;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @MockitoBean SmsSender sms;
    private final AtomicReference<String> sentMessage = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        when(sms.available()).thenReturn(true);
        doAnswer(invocation -> {
            sentMessage.set(invocation.getArgument(1));
            return null;
        }).when(sms).send(anyString(), anyString());
    }

    @Test
    void verifiedPhoneCreatesAccountAndBearerTokenForPrivateApi() {
        auth.requestCode("010-1234-5678");
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        assertThat(code).matches("[0-9]{6}");
        assertThatThrownBy(() -> auth.login("01012345678", "000000".equals(code) ? "000001" : "000000", "ELDER"))
                .isInstanceOf(ResponseStatusException.class);
        PhoneAuthService.LoginResult result = auth.login("+821012345678", code, "ELDER");
        Jwt jwt = decoder.decode(result.accessToken());
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(accounts.current(jwt).userId()).isEqualTo(result.userId());
        assertThat(jwt.getAudience()).contains(SecurityConfig.AUDIENCE);
        assertThat(jwt.getSubject()).doesNotContain("01012345678");
        assertThatThrownBy(() -> auth.login("01012345678", code, "ELDER"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT attempts FROM phone_verifications", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> auth.requestCode("01012345678"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
    }

    @Test
    void demoPhoneLogsInWithFixedCodeWithoutSms() {
        when(sms.available()).thenReturn(false);
        auth.requestCode("010-0000-0001");
        auth.requestCode("010-0000-0001");
        assertThat(auth.login("01000000001", "135790", "ELDER").role()).isEqualTo("ELDER");
        verify(sms, never()).send(anyString(), anyString());
        assertThatThrownBy(() -> auth.requestCode("01012345678"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
    }

    @Test
    void roleCannotBeChangedOnSubsequentLogin() {
        auth.requestCode("01098765432");
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        auth.login("01098765432", code, "GUARDIAN");
        db.update("UPDATE phone_verifications SET sent_at = TIMESTAMP '2000-01-01 00:00:00' WHERE phone_hash IS NOT NULL");
        auth.requestCode("01098765432");
        String secondCode = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        assertThatThrownBy(() -> auth.login("01098765432", secondCode, "ELDER"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    void httpLoginRequiresCodeAndIssuedTokenOpensPrivateEndpoint() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/phone-verifications")
                .contentType("application/json")
                .content("{\"phoneNumber\":\"01011112222\"}"))
                .andExpect(status().isAccepted());
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        mvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"phoneNumber\":\"01011112222\",\"verificationCode\":\"999999\",\"role\":\"ELDER\"}"))
                .andExpect(status().isBadRequest());
        PhoneAuthService.LoginResult login = auth.login("01011112222", code, "ELDER");
        mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void expiredCodeAndFiveWrongAttemptsCannotAuthenticate() {
        auth.requestCode("01022223333");
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.login("01022223333", wrong, "ELDER"))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> auth.login("01022223333", code, "ELDER"))
                .isInstanceOf(ResponseStatusException.class);
        db.update("UPDATE phone_verifications SET attempts = 0, expires_at = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
        assertThatThrownBy(() -> auth.login("01022223333", code, "ELDER"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refreshRotatesTokenAndLogoutEndsThatDeviceSession() {
        auth.requestCode("01033334444");
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        PhoneAuthService.LoginResult first = auth.login("01033334444", code, "GUARDIAN");
        assertThat(first.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(first.refreshExpiresAt()).isAfter(first.expiresAt());
        assertThat(db.queryForObject("SELECT refresh_token_hash FROM auth_sessions", String.class))
                .doesNotContain(first.refreshToken());

        PhoneAuthService.LoginResult renewed = auth.refresh(first.refreshToken());
        assertThat(renewed.userId()).isEqualTo(first.userId());
        assertThat(renewed.role()).isEqualTo("GUARDIAN");
        assertThat(renewed.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(renewed.refreshExpiresAt()).isAfter(first.refreshExpiresAt());
        assertThat(accounts.current(decoder.decode(renewed.accessToken())).userId()).isEqualTo(first.userId());
        assertThatThrownBy(() -> auth.refresh(first.refreshToken()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");

        auth.logout(renewed.refreshToken());
        assertThatThrownBy(() -> auth.refresh(renewed.refreshToken()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> decoder.decode(renewed.accessToken())).isInstanceOf(Exception.class);
    }

    @Test
    void expiredRefreshTokenCannotIssueAnotherAccessToken() {
        auth.requestCode("01055556666");
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        PhoneAuthService.LoginResult login = auth.login("01055556666", code, "ELDER");
        db.update("UPDATE auth_sessions SET expires_at = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)));
        assertThatThrownBy(() -> auth.refresh(login.refreshToken()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");
    }

    @Test
    void loggingOutOneDeviceDoesNotEndAnotherDeviceSession() {
        auth.requestCode("01099990000");
        String firstCode = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        PhoneAuthService.LoginResult first = auth.login("01099990000", firstCode, "ELDER");
        db.update("UPDATE phone_verifications SET sent_at = TIMESTAMP '2000-01-01 00:00:00' " +
                "WHERE phone_hash IS NOT NULL");
        auth.requestCode("01099990000");
        String secondCode = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        PhoneAuthService.LoginResult second = auth.login("01099990000", secondCode, "ELDER");

        auth.logout(first.refreshToken());
        assertThatThrownBy(() -> decoder.decode(first.accessToken())).isInstanceOf(Exception.class);
        assertThat(accounts.current(decoder.decode(second.accessToken())).userId()).isEqualTo(first.userId());
        assertThat(auth.refresh(second.refreshToken()).userId()).isEqualTo(second.userId());
    }

    @Test
    void refreshAndLogoutEndpointsWorkWithoutAnUnexpiredAccessToken() throws Exception {
        auth.requestCode("01077778888");
        String code = sentMessage.get().replaceAll(".*인증번호 ([0-9]{6}).*", "$1");
        PhoneAuthService.LoginResult login = auth.login("01077778888", code, "ELDER");
        String refreshed = mvc.perform(post("/api/v1/auth/refresh")
                .contentType("application/json")
                .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(refreshed).contains("\"refreshToken\"").doesNotContain(login.refreshToken());
        String replacement = new com.fasterxml.jackson.databind.ObjectMapper().readTree(refreshed)
                .get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/refresh")
                .contentType("application/json")
                .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout")
                .contentType("application/json")
                .content("{\"refreshToken\":\"" + replacement + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh")
                .contentType("application/json")
                .content("{\"refreshToken\":\"" + replacement + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
