package com.yongyong.backend.recommendation;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyong.backend.appointment.extraction.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClientException;

@SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@AutoConfigureMockMvc
class RecommendationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiClient geminiClient;

    private ResultActions recommend(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/department-recommendations").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 버튼으로_고르면_AI_없이_진료과를_돌려준다() throws Exception {
        recommend("{\"bodyPart\":\"뼈·관절·허리\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergency").value(false))
                .andExpect(jsonPath("$.department").value("정형외과"));
        verify(geminiClient, never()).generateJson(any(), any(), any());
    }

    @Test
    void 위험_증상은_AI_없이_119로_보낸다() throws Exception {
        recommend("{\"text\":\"아까부터 가슴이 막 짓눌리는 것 같아\"}")
                .andExpect(jsonPath("$.emergency").value(true))
                .andExpect(jsonPath("$.department").value("119 신고"));
        recommend("{\"bodyPart\":\"위험 증상\"}").andExpect(jsonPath("$.emergency").value(true));
        verify(geminiClient, never()).generateJson(any(), any(), any());
    }

    @Test
    void 말로_하면_AI가_고른_아픈_곳의_진료과를_돌려준다() throws Exception {
        given(geminiClient.generateJson(any(), any(), any()))
                .willReturn("{\"emergency\":false,\"bodyPart\":\"뼈·관절·허리\"}");
        recommend("{\"text\":\"무릎이 시려서 계단을 못 올라가\"}")
                .andExpect(jsonPath("$.bodyPart").value("뼈·관절·허리"))
                .andExpect(jsonPath("$.department").value("정형외과"));
    }

    @Test
    void AI가_실패하면_버튼으로_고르게_빈_값을_준다() throws Exception {
        given(geminiClient.generateJson(any(), any(), any())).willThrow(new RestClientException("503"));
        recommend("{\"text\":\"무릎이 시려\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergency").value(false))
                .andExpect(jsonPath("$.department").isEmpty());
    }

    @Test
    void 모르는_아픈_곳이나_빈_요청은_400() throws Exception {
        recommend("{\"bodyPart\":\"발톱\"}").andExpect(status().isBadRequest());
        recommend("{\"text\":\" \"}").andExpect(status().isBadRequest());
    }

    @Test
    void 구의_진료과_병원을_요양병원_빼고_3곳_준다() throws Exception {
        mockMvc.perform(get("/api/v1/clinics").with(jwt()).param("department", "정형외과").param("district", "처인구"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("서울본정형외과의원"))
                .andExpect(jsonPath("$[0].clinicSize").value("SMALL"))
                .andExpect(jsonPath("$[0].stopDistanceM").value(30))
                .andExpect(jsonPath("$[1].name").value("동진정형외과의원"));
    }

    @Test
    void 토큰_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/clinics").param("department", "정형외과")).andExpect(status().isUnauthorized());
    }
}
