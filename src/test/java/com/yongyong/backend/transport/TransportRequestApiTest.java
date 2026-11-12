package com.yongyong.backend.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yongyong.backend.account.AccountService;
import java.time.Instant;
import java.util.UUID;
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
class TransportRequestApiTest {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate db;

    private void insertAppointment(String appointmentId, String elderId) {
        db.update("""
                INSERT INTO appointments(appointment_id, elder_id, clinic_name, appointment_at, source, status,
                                         created_at, updated_at)
                VALUES (?, ?, 'OO병원', CURRENT_TIMESTAMP, 'MANUAL', 'CONFIRMED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, appointmentId, elderId);
    }

    @Test
    void createsIdempotentRequestAndEncryptsSensitiveDetails() throws Exception {
        Jwt user = token("transport-owner");
        accounts.login(user, "ELDER");
        String elderId = accounts.createProfile(user, "이동요청 테스트", 1950, null, null).elderId();
        String appointmentId = UUID.randomUUID().toString();
        insertAppointment(appointmentId, elderId);
        String requestBody = """
                {"type":"ACCESSIBLE_TRANSPORT","pickupLocation":{"name":"집 주소",
                 "latitude":37.27,"longitude":127.15},"note":"현관에서 기다려주세요"}
                """;

        String first = mvc.perform(post("/api/v1/appointments/{id}/transport-requests", appointmentId)
                        .with(jwt().jwt(user)).header("Idempotency-Key", "transport-once")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(first, "$.data.requestId");
        String retry = mvc.perform(post("/api/v1/appointments/{id}/transport-requests", appointmentId)
                        .with(jwt().jwt(user)).header("Idempotency-Key", "transport-once")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(retry, "$.data.requestId")).isEqualTo(requestId);
        String otherAppointmentId = UUID.randomUUID().toString();
        insertAppointment(otherAppointmentId, elderId);
        String otherAppointmentRequest = mvc.perform(post("/api/v1/appointments/{id}/transport-requests", otherAppointmentId)
                        .with(jwt().jwt(user)).header("Idempotency-Key", "transport-once")
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(otherAppointmentRequest, "$.data.requestId")).isNotEqualTo(requestId);

        String encryptedNote = db.queryForObject("SELECT note_enc FROM transport_requests WHERE request_id = ?",
                String.class, UUID.fromString(requestId));
        String encryptedPickup = db.queryForObject("SELECT pickup_data_enc FROM transport_requests WHERE request_id = ?",
                String.class, UUID.fromString(requestId));
        assertThat(encryptedNote).doesNotContain("현관에서 기다려주세요");
        assertThat(encryptedPickup).doesNotContain("집 주소");

        mvc.perform(get("/api/v1/appointments/{id}/transport-requests", appointmentId).with(jwt().jwt(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].requestId").value(requestId));
    }

    @Test
    void elderCanRequestGuardianAssistanceAndLinkedGuardianCanAnswerOnce() throws Exception {
        Jwt elder = token("assistance-elder");
        accounts.login(elder, "ELDER");
        String elderId = accounts.createProfile(elder, "요청 어르신", 1948, null, null).elderId();
        accounts.recordConsent(elder, elderId, "CAREGIVER_SHARING", "v1", true);
        Jwt guardian = token("assistance-guardian");
        var guardianUser = accounts.login(guardian, "GUARDIAN");
        db.update("INSERT INTO guardian_links(link_id, elder_id, guardian_user_id, relationship, status, linked_at) VALUES (?, ?, ?, '자녀', 'ACTIVE', CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), elderId, guardianUser.userId());
        String appointmentId = UUID.randomUUID().toString();
        insertAppointment(appointmentId, elderId);

        String created = mvc.perform(post("/api/v1/appointments/{id}/transport-requests", appointmentId)
                        .with(jwt().jwt(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GUARDIAN_ASSISTANCE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.statusDescription").value("보호자 응답 대기 중입니다."))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.data.requestId");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM push_outbox WHERE recipient_user_id = ? AND notification_type = 'GUARDIAN_ASSISTANCE_REQUEST'",
                Integer.class, guardianUser.userId())).isEqualTo(1);

        mvc.perform(post("/api/v1/appointments/{appointmentId}/transport-requests/{requestId}/response", appointmentId, requestId)
                        .with(jwt().jwt(guardian)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"PICKUP_BY_GUARDIAN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.guardianResponse").value("PICKUP_BY_GUARDIAN"));
        mvc.perform(post("/api/v1/appointments/{appointmentId}/transport-requests/{requestId}/response", appointmentId, requestId)
                        .with(jwt().jwt(guardian)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"CALL_TAXI\"}"))
                .andExpect(status().isConflict());
    }

    private static Jwt token(String subject) {
        return Jwt.withTokenValue("token").header("alg", "HS256")
                .issuer("https://auth.yongyongthon.example/phone").subject(subject)
                .audience(java.util.List.of("yongyongthon-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }
}
