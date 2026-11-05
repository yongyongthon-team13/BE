package com.yongyong.backend.companion;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yongyong.backend.account.AccountService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@AutoConfigureMockMvc
@Transactional
class CompanionGuardianApiTest {

    private static final String APPOINTMENTS = "/api/v1/appointments";
    private static final String GUARDIAN = "/api/v1/guardian/elder-profiles";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    private static Jwt token(String subject) {
        return Jwt.withTokenValue(subject).header("alg", "HS256")
                .issuer("https://id.example.test").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    private String elder(Jwt user, AccountService.MobilityUpdate mobility) {
        accounts.login(user, "ELDER");
        return accounts.createProfile(user, "김순자", 1950, null, mobility).elderId();
    }

    private String appointment(Jwt user, String elderId, String at) throws Exception {
        String body = """
                {"elderId":"%s","clinicName":"OO병원","appointmentAt":"%s","source":"MANUAL"}"""
                .formatted(elderId, at);
        return JsonPath.read(mockMvc.perform(post(APPOINTMENTS).with(jwt().jwt(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.appointmentId");
    }

    private Jwt linkedGuardian(Jwt elder, String elderId, String subject) {
        Jwt guardian = token(subject);
        accounts.login(guardian, "GUARDIAN");
        accounts.recordConsent(elder, elderId, "CAREGIVER_SHARING", "v1", true);
        accounts.connectGuardian(guardian, accounts.issueLinkCode(elder, elderId).code(), "자녀");
        return guardian;
    }

    private ResultActions assess(Jwt user, String appointmentId) throws Exception {
        return mockMvc.perform(post(APPOINTMENTS + "/" + appointmentId + "/companion-assessments").with(jwt().jwt(user)));
    }

    private ResultActions request(Jwt user, String appointmentId) throws Exception {
        return mockMvc.perform(post(APPOINTMENTS + "/" + appointmentId + "/companion-requests").with(jwt().jwt(user))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    @Test
    void 이동이_어려우면_동행_추천() throws Exception {
        Jwt hard = token("companion-hard");
        String hardElder = elder(hard, new AccountService.MobilityUpdate("WHEELCHAIR", true, null, null));
        assess(hard, appointment(hard, hardElder, "2030-01-01T10:00:00+09:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommended").value(true))
                .andExpect(jsonPath("$.reasonCodes[0]").value("MOBILITY_AID_WHEELCHAIR"))
                .andExpect(jsonPath("$.reasonCodes[1]").value("STAIRS_DIFFICULT"));

        Jwt easy = token("companion-easy");
        String easyElder = elder(easy, null);
        assess(easy, appointment(easy, easyElder, "2030-01-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.recommended").value(false))
                .andExpect(jsonPath("$.reasonCodes", hasSize(0)));
    }

    @Test
    void 동행_신청은_중복_불가_남의_일정은_404() throws Exception {
        Jwt user = token("companion-request");
        String appointmentId = appointment(user, elder(user, null), "2030-01-01T10:00:00+09:00");

        request(user, appointmentId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bookingMethod").value("PHONE"));
        request(user, appointmentId).andExpect(status().isConflict());

        Jwt stranger = token("companion-stranger");
        elder(stranger, null);
        request(stranger, appointmentId).andExpect(status().isNotFound());
        assess(stranger, appointmentId).andExpect(status().isNotFound());
    }

    @Test
    void 보호자는_연결된_어르신의_다가오는_일정만_본다() throws Exception {
        Jwt user = token("guardian-elder");
        String elderId = elder(user, null);
        appointment(user, elderId, "2020-01-01T10:00:00+09:00");
        String upcoming = appointment(user, elderId, "2030-01-01T10:00:00+09:00");
        Jwt guardian = linkedGuardian(user, elderId, "guardian-linked");

        mockMvc.perform(get(GUARDIAN).with(jwt().jwt(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].elderId").value(elderId))
                .andExpect(jsonPath("$[0].name").value("김순자"))
                .andExpect(jsonPath("$[0].relationship").value("자녀"));
        mockMvc.perform(get(GUARDIAN + "/" + elderId + "/appointments").with(jwt().jwt(guardian)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].appointmentId").value(upcoming));

        Jwt other = token("guardian-other");
        accounts.login(other, "GUARDIAN");
        mockMvc.perform(get(GUARDIAN).with(jwt().jwt(other))).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(GUARDIAN + "/" + elderId + "/appointments").with(jwt().jwt(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(GUARDIAN).with(jwt().jwt(user))).andExpect(status().isForbidden());

        accounts.recordConsent(user, elderId, "CAREGIVER_SHARING", "v1", false);
        mockMvc.perform(get(GUARDIAN).with(jwt().jwt(guardian))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 보호자_일정_입력_보조() throws Exception {
        Jwt user = token("assist-elder");
        String elderId = elder(user, null);
        String appointmentId = appointment(user, elderId, "2030-01-01T10:00:00+09:00");
        Jwt guardian = linkedGuardian(user, elderId, "assist-guardian");
        String url = GUARDIAN + "/" + elderId + "/appointments/" + appointmentId + "/assist";

        mockMvc.perform(post(url).with(jwt().jwt(guardian)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"정형외과\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("정형외과"))
                .andExpect(jsonPath("$.source").value("GUARDIAN"));

        mockMvc.perform(post(url).with(jwt().jwt(user)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        Jwt otherUser = token("assist-other-elder");
        String otherElder = elder(otherUser, null);
        mockMvc.perform(post(GUARDIAN + "/" + otherElder + "/appointments/" + appointmentId + "/assist")
                        .with(jwt().jwt(guardian)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
