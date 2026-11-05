package com.yongyong.backend.appointment;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yongyong.backend.account.AccountService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@AutoConfigureMockMvc
@Transactional
class AppointmentApiTest {

    private static final String URL = "/api/v1/appointments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate db;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private static Jwt token(String subject) {
        return Jwt.withTokenValue(subject).header("alg", "HS256")
                .issuer("https://id.example.test").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    private String elderOf(Jwt user) {
        accounts.login(user, "ELDER");
        return accounts.createProfile(user, "테스트", 1950, null, null).elderId();
    }

    @Test
    void 등록_조회_수정_목록() throws Exception {
        Jwt user = token("appointment-owner");
        UUID elderId = UUID.fromString(elderOf(user));
        String body = """
                {"elderId":"%s","clinicName":"OO병원","clinicAddress":"용인시 처인구","department":"정형외과",
                 "purpose":"진료","location":"본관 2층","preparations":["금식","신분증 지참"],"needsGuardian":true,
                 "appointmentAt":"2026-11-12T10:30:00+09:00","source":"SMS"}""".formatted(elderId);

        String created = mockMvc.perform(post(URL).with(jwt().jwt(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.preparations", hasSize(2)))
                .andExpect(jsonPath("$.needsGuardian").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.appointmentId");
        entityManager.flush();
        org.assertj.core.api.Assertions.assertThat(db.queryForObject(
                        "SELECT clinic_address_enc FROM appointments WHERE appointment_id = ?", String.class, id))
                .doesNotContain("용인시");

        mockMvc.perform(get(URL + "/" + id).with(jwt().jwt(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("OO병원"))
                .andExpect(jsonPath("$.clinicAddress").value("용인시 처인구"))
                .andExpect(jsonPath("$.appointmentAt").value("2026-11-12T10:30:00+09:00"));

        mockMvc.perform(patch(URL + "/" + id).with(jwt().jwt(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.department").value("정형외과"));

        mockMvc.perform(get(URL).with(jwt().jwt(user)).param("elderId", elderId.toString())
                        .param("from", "2026-11-12T00:00:00+09:00")
                        .param("to", "2026-11-13T00:00:00+09:00"))
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get(URL).with(jwt().jwt(user)).param("elderId", elderId.toString())
                        .param("from", "2026-11-13T00:00:00+09:00")
                        .param("to", "2026-11-14T00:00:00+09:00"))
                .andExpect(jsonPath("$", hasSize(0)));

        Jwt stranger = token("appointment-stranger");
        elderOf(stranger);
        mockMvc.perform(get(URL + "/" + id).with(jwt().jwt(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(get(URL).with(jwt().jwt(stranger)).param("elderId", elderId.toString())
                        .param("from", "2026-11-12T00:00:00+09:00")
                        .param("to", "2026-11-13T00:00:00+09:00"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 토큰_없으면_401() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void 필수값_없으면_400() throws Exception {
        mockMvc.perform(post(URL).with(jwt().jwt(token("appointment-invalid"))).contentType(MediaType.APPLICATION_JSON).content("{\"clinicName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_일정은_404() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID()).with(jwt().jwt(token("appointment-missing"))))
                .andExpect(status().isNotFound());
    }
}
