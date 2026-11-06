package com.yongyong.backend.appointment.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class GeminiClientTest {

    private static final String OK = """
            {"steps":[{"type":"thought"},{"type":"model_output","content":[{"type":"text","text":"{\\"items\\":[]}"}]}]}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GeminiClient client = new GeminiClient(builder, "key", "main", List.of("backup"), Duration.ZERO);

    private String call() {
        return client.generateJson("system", "input", JsonMapper.builder().build().readTree("{}"));
    }

    @Test
    void 재시도_후_다음_모델로_넘어간다() {
        server.expect(jsonPath("$.model").value("main")).andRespond(withServiceUnavailable());
        server.expect(jsonPath("$.model").value("main")).andRespond(withServiceUnavailable());
        server.expect(jsonPath("$.model").value("backup"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(call()).isEqualTo("{\"items\":[]}");
        server.verify();
    }

    @Test
    void 잘못된_요청은_재시도하지_않는다() {
        server.expect(jsonPath("$.model").value("main")).andRespond(withBadRequest());

        assertThatThrownBy(this::call).isInstanceOf(HttpClientErrorException.BadRequest.class);
        server.verify();
    }
}
