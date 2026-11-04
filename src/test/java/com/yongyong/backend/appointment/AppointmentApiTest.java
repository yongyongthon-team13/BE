package com.yongyong.backend.appointment;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentApiTest {

    private static final String URL = "/api/v1/appointments";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 등록_조회_수정_목록() throws Exception {
        UUID elderId = UUID.randomUUID();
        String body = """
                {"elderId":"%s","clinicName":"OO병원","department":"정형외과",
                 "appointmentAt":"2026-11-12T10:30:00+09:00","source":"SMS"}""".formatted(elderId);

        String created = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.appointmentId");

        mockMvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("OO병원"))
                .andExpect(jsonPath("$.appointmentAt").value("2026-11-12T10:30:00+09:00"));

        mockMvc.perform(patch(URL + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.department").value("정형외과"));

        mockMvc.perform(get(URL).param("elderId", elderId.toString())
                        .param("from", "2026-11-12T00:00:00+09:00")
                        .param("to", "2026-11-13T00:00:00+09:00"))
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get(URL).param("elderId", elderId.toString())
                        .param("from", "2026-11-13T00:00:00+09:00")
                        .param("to", "2026-11-14T00:00:00+09:00"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 필수값_없으면_400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"clinicName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없는_일정은_404() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
