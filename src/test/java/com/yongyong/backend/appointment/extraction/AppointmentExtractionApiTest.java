package com.yongyong.backend.appointment.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClientException;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentExtractionApiTest {

    private static final String BODY = """
            {"text":"[OO병원] 11/12 10:30 정형외과 예약. 변경 031-000-0000",
             "receivedAt":"2026-11-04T09:00:00+09:00"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiClient geminiClient;

    private static String item(String clinicName, String appointmentAt) {
        return """
                {"items":[{"type":"NEW","clinicName":%s,"department":"정형외과","appointmentAt":"%s",
                "previousAt":null,"location":null,"preparations":[],"needsGuardian":false,
                "needsReview":false,"confidence":0.9}]}""".formatted(clinicName, appointmentAt);
    }

    private ResultActions extract() throws Exception {
        return mockMvc.perform(post("/api/v1/appointment-extractions")
                .contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    @Test
    void 전화번호는_가리고_보내고_결과를_돌려준다() throws Exception {
        given(geminiClient.generateJson(any(), argThat(input ->
                input.contains("2026-11-04T09:00+09:00") && input.contains("[전화번호]") && !input.contains("031-000-0000")),
                any())).willReturn(item("\"OO병원\"", "2026-11-12T10:30:00+09:00"));

        extract().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].clinicName").value("OO병원"))
                .andExpect(jsonPath("$.items[0].appointmentAt").value("2026-11-12T10:30:00+09:00"))
                .andExpect(jsonPath("$.items[0].needsReview").value(false));
    }

    @Test
    void 지난_날짜나_병원명_없으면_확인_필요() throws Exception {
        given(geminiClient.generateJson(any(), any(), any())).willReturn(item("\"OO병원\"", "2026-01-01T10:30:00+09:00"));
        extract().andExpect(jsonPath("$.items[0].needsReview").value(true));

        given(geminiClient.generateJson(any(), any(), any())).willReturn(item("null", "2026-11-12T10:30:00+09:00"));
        extract().andExpect(jsonPath("$.items[0].needsReview").value(true));
    }

    @Test
    void 호출_실패나_깨진_응답은_503() throws Exception {
        given(geminiClient.generateJson(any(), any(), any())).willThrow(new RestClientException("429"));
        extract().andExpect(status().isServiceUnavailable());

        org.mockito.Mockito.reset(geminiClient);
        given(geminiClient.generateJson(any(), any(), any())).willReturn("not json");
        extract().andExpect(status().isServiceUnavailable());
    }

    @Test
    void 빈_문자는_400() throws Exception {
        mockMvc.perform(post("/api/v1/appointment-extractions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 전화번호_형식별_마스킹() {
        assertThat(AppointmentExtractionService.maskPhoneNumbers("010-1234-5678 1588-0000 0312345678 11/12 10:30"))
                .isEqualTo("[전화번호] [전화번호] [전화번호] 11/12 10:30");
    }
}
