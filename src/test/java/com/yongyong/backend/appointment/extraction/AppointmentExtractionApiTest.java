package com.yongyong.backend.appointment.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import java.util.Map;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClientException;

@SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@AutoConfigureMockMvc
class AppointmentExtractionApiTest {

    private static final String BODY = """
            {"text":"[OO병원] 11/12 10:30 정형외과 예약. 변경 031-000-0000",
             "receivedAt":"2026-11-04T09:00:00+09:00"}""";

    private static int nextText;

    private static String uniqueBody() {
        return """
                {"text":"[OO병원] 11/12 10:30 예약 %d","receivedAt":"2026-11-04T09:00:00+09:00"}"""
                .formatted(++nextText);
    }

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
        return extract(BODY);
    }

    private ResultActions extract(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/appointment-extractions").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 전화번호는_가리고_보내고_결과를_돌려준다() throws Exception {
        given(geminiClient.generateJson(any(), argThat(input ->
                input.toString().contains("2026-11-04T09:00+09:00") && input.toString().contains("[전화번호]")
                        && !input.toString().contains("031-000-0000")),
                any())).willReturn(item("\"OO병원\"", "2026-11-12T10:30:00+09:00"));

        extract().andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].clinicName").value("OO병원"))
                .andExpect(jsonPath("$.items[0].appointmentAt").value("2026-11-12T10:30:00+09:00"))
                .andExpect(jsonPath("$.items[0].needsReview").value(false));
    }

    @Test
    void 지난_날짜나_병원명_없으면_확인_필요() throws Exception {
        given(geminiClient.generateJson(any(), any(), any())).willReturn(item("\"OO병원\"", "2026-01-01T10:30:00+09:00"));
        extract(uniqueBody()).andExpect(jsonPath("$.items[0].needsReview").value(true));

        given(geminiClient.generateJson(any(), any(), any())).willReturn(item("null", "2026-11-12T10:30:00+09:00"));
        extract(uniqueBody()).andExpect(jsonPath("$.items[0].needsReview").value(true));
    }

    @Test
    void 호출_실패나_깨진_응답은_503() throws Exception {
        given(geminiClient.generateJson(any(), any(), any())).willThrow(new RestClientException("429"));
        extract(uniqueBody()).andExpect(status().isServiceUnavailable());

        org.mockito.Mockito.reset(geminiClient);
        given(geminiClient.generateJson(any(), any(), any())).willReturn("not json");
        extract(uniqueBody()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void 같은_문자는_다시_호출하지_않는다() throws Exception {
        String body = uniqueBody();
        given(geminiClient.generateJson(any(), any(), any())).willReturn(item("\"OO병원\"", "2026-11-12T10:30:00+09:00"));
        extract(body).andExpect(status().isOk());
        extract(body.replace("09:00:00", "09:30:00")).andExpect(status().isOk());
        verify(geminiClient, times(1)).generateJson(any(), any(), any());
    }

    @Test
    void 스크린샷을_이미지로_보낸다() throws Exception {
        given(geminiClient.generateJson(any(), argThat(input -> input instanceof List<?> parts
                        && ((Map<?, ?>) parts.get(1)).get("type").equals("image")
                        && ((Map<?, ?>) parts.get(1)).get("mime_type").equals("image/png")
                        && ((Map<?, ?>) parts.get(1)).get("data").equals("cG5n")), any()))
                .willReturn(item("\"OO병원\"", "2026-11-12T10:30:00+09:00"));

        mockMvc.perform(multipart("/api/v1/appointment-extractions").with(jwt())
                        .file(new MockMultipartFile("image", "s.png", "image/png", "png".getBytes()))
                        .param("receivedAt", "2026-11-04T09:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].clinicName").value("OO병원"));
    }

    @Test
    void 이미지가_아니면_415() throws Exception {
        mockMvc.perform(multipart("/api/v1/appointment-extractions").with(jwt())
                        .file(new MockMultipartFile("image", "s.pdf", "application/pdf", "x".getBytes())))
                .andExpect(status().isUnsupportedMediaType());
        verify(geminiClient, never()).generateJson(any(), any(), any());
    }

    @Test
    void 토큰_없으면_401() throws Exception {
        mockMvc.perform(post("/api/v1/appointment-extractions")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 빈_문자는_400() throws Exception {
        mockMvc.perform(post("/api/v1/appointment-extractions").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 병원_규모_이름_규칙() {
        assertThat(AppointmentExtractionService.clinicSizeOf("OO대학교병원")).isEqualTo("LARGE");
        assertThat(AppointmentExtractionService.clinicSizeOf("OO내과의원")).isEqualTo("SMALL");
        assertThat(AppointmentExtractionService.clinicSizeOf("OO안과")).isEqualTo("SMALL");
        assertThat(AppointmentExtractionService.clinicSizeOf("OO병원")).isEqualTo("MEDIUM");
        assertThat(AppointmentExtractionService.clinicSizeOf(null)).isNull();
    }

    @Test
    void 전화번호_형식별_마스킹() {
        assertThat(AppointmentExtractionService.maskPhoneNumbers("010-1234-5678 1588-0000 0312345678 11/12 10:30"))
                .isEqualTo("[전화번호] [전화번호] [전화번호] 11/12 10:30");
    }
}
