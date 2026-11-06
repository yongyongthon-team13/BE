package com.yongyong.backend.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yongyong.backend.weather.KmaClient.ForecastItem;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClientException;

@SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@AutoConfigureMockMvc
class WeatherRiskApiTest {

    private static final OffsetDateTime DEPARTURE =
            OffsetDateTime.now(ZoneOffset.ofHours(9)).plusHours(3).withMinute(0).withSecond(0).withNano(0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KmaClient kmaClient;

    private void forecast(Map<String, String> values) {
        String date = DEPARTURE.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String time = DEPARTURE.format(DateTimeFormatter.ofPattern("HHmm"));
        List<ForecastItem> items = new ArrayList<>();
        values.forEach((category, value) -> items.add(new ForecastItem(category, date, time, value)));
        given(kmaClient.getForecast(any(), any(), anyInt(), anyInt())).willReturn(items);
    }

    private ResultActions assess(double latitude, double longitude, OffsetDateTime departureAt) throws Exception {
        return mockMvc.perform(get("/api/v1/weather-risk").with(jwt())
                .param("latitude", String.valueOf(latitude))
                .param("longitude", String.valueOf(longitude))
                .param("for", departureAt.toString()));
    }

    @Test
    void 맑으면_NORMAL() throws Exception {
        forecast(Map.of("PTY", "0", "POP", "10", "PCP", "강수없음", "SNO", "적설없음", "WSD", "2", "TMP", "15"));
        given(kmaClient.getCurrentWarnings()).willReturn("o 없음");

        assess(37.241, 127.178, DEPARTURE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("NORMAL"))
                .andExpect(jsonPath("$.warningChecked").value(true))
                .andExpect(jsonPath("$.temperatureC").value(15))
                .andExpect(jsonPath("$.simulated").value(false));
    }

    @Test
    void 비오면_CAUTION_폭우면_WARNING() throws Exception {
        given(kmaClient.getCurrentWarnings()).willReturn("");

        forecast(Map.of("PTY", "1", "POP", "80", "PCP", "1mm 미만", "WSD", "2", "TMP", "15"));
        assess(37.241, 127.178, DEPARTURE)
                .andExpect(jsonPath("$.riskLevel").value("CAUTION"))
                .andExpect(jsonPath("$.hazardCodes[0]").value("RAIN"));

        forecast(Map.of("PTY", "1", "POP", "90", "PCP", "30.0~50.0mm", "WSD", "2", "TMP", "15"));
        assess(37.241, 127.178, DEPARTURE).andExpect(jsonPath("$.riskLevel").value("WARNING"));
    }

    @Test
    void 용인_특보면_맑아도_WARNING() throws Exception {
        forecast(Map.of("PTY", "0", "POP", "10", "WSD", "2", "TMP", "15"));
        given(kmaClient.getCurrentWarnings()).willReturn("o 호우주의보 : 경기도(용인, 수원)\r\no 풍랑주의보 : 서해");

        assess(37.241, 127.178, DEPARTURE)
                .andExpect(jsonPath("$.riskLevel").value("WARNING"))
                .andExpect(jsonPath("$.warnings[0]").value("호우주의보"));
    }

    @Test
    void 특보_실패나_용인_밖이면_확인_안_된_것으로_표시() throws Exception {
        forecast(Map.of("PTY", "0", "POP", "10", "WSD", "2", "TMP", "15"));
        given(kmaClient.getCurrentWarnings()).willThrow(new RestClientException("timeout"));

        assess(37.241, 127.178, DEPARTURE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warningChecked").value(false));
        assess(37.5665, 126.978, DEPARTURE).andExpect(jsonPath("$.warningChecked").value(false));
    }

    @Test
    void 예보_실패는_503_범위밖은_400_좌표오류는_400() throws Exception {
        forecast(Map.of("PTY", "0", "TMP", "15"));
        assess(37.241, 127.178, DEPARTURE.plusDays(10)).andExpect(status().isBadRequest());
        assess(137.241, 127.178, DEPARTURE).andExpect(status().isBadRequest());

        given(kmaClient.getForecast(any(), any(), anyInt(), anyInt())).willThrow(new IllegalStateException("03"));
        assess(37.241, 127.178, DEPARTURE).andExpect(status().isServiceUnavailable());
    }

    @Test
    void 토큰_없으면_401() throws Exception {
        mockMvc.perform(get("/api/v1/weather-risk").param("latitude", "37.2").param("longitude", "127.1")
                        .param("for", DEPARTURE.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 격자_변환() {
        assertThat(GridConverter.toGrid(37.5665, 126.978)).isEqualTo(new GridConverter.Grid(60, 127));
        assertThat(GridConverter.toGrid(35.1796, 129.0756)).isEqualTo(new GridConverter.Grid(98, 76));
    }

    @Test
    void 최근_발표시각() {
        assertThat(base("2026-11-05T01:00")).isEqualTo("2026-11-04T23:00");
        assertThat(base("2026-11-05T02:09")).isEqualTo("2026-11-04T23:00");
        assertThat(base("2026-11-05T02:10")).isEqualTo("2026-11-05T02:00");
        assertThat(base("2026-11-05T05:09")).isEqualTo("2026-11-05T02:00");
        assertThat(base("2026-11-05T12:00")).isEqualTo("2026-11-05T11:00");
        assertThat(base("2026-11-05T23:30")).isEqualTo("2026-11-05T23:00");
        assertThat(base("2026-11-06T00:05")).isEqualTo("2026-11-05T23:00");
    }

    private static String base(String now) {
        return WeatherRiskService.latestBaseTime(LocalDateTime.parse(now)).toString();
    }

    @Test
    void 특보_문구_파싱() {
        assertThat(WeatherRiskService.warningsForYongin("o 대설주의보 : 경기도")).containsExactly("대설주의보");
        assertThat(WeatherRiskService.warningsForYongin("o 한파경보 : 강원도, 경기도(용인, 이천)")).containsExactly("한파경보");
        assertThat(WeatherRiskService.warningsForYongin("o 폭염주의보 : 경기도(수원)")).isEmpty();
        assertThat(WeatherRiskService.warningsForYongin("o 강풍주의보 : 전라남도(흑산도.홍도), 제주도")).isEmpty();
        assertThat(WeatherRiskService.warningsForYongin("o 건조주의보 : 경기도")).isEmpty();
    }
}
