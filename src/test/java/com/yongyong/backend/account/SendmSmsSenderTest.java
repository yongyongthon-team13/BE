package com.yongyong.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SendmSmsSenderTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOneNonAdvertisingSmsWithTheConfiguredCredentials() throws Exception {
        AtomicReference<JsonNode> sentBody = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/sms/send", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("user-id")).isEqualTo("account-id");
            assertThat(exchange.getRequestHeaders().getFirst("api-key")).isEqualTo("test-api-key");
            requestId.set(exchange.getRequestHeaders().getFirst("request-id"));
            sentBody.set(json.readTree(exchange.getRequestBody()));
            byte[] response = "{\"code\":\"0\",\"data\":{\"msgId\":1,\"smsType\":\"SMS\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        SendmSmsSender sms = sender();
        sms.send("+821012345678", "[용용톤] 인증번호 123456 (5분 유효)");

        assertThat(requestId.get()).matches("[0-9a-f-]{36}");
        assertThat(sentBody.get().path("callerNo").asText()).isEqualTo("01099998888");
        assertThat(sentBody.get().path("receiveNos").asText()).isEqualTo("01012345678");
        assertThat(sentBody.get().path("smsType").asText()).isEqualTo("SMS");
        assertThat(sentBody.get().path("adYn").asText()).isEqualTo("N");
        assertThat(sentBody.get().path("message").asText()).contains("123456");
    }

    @Test
    void rejectsProviderErrorsWithoutClaimingThatACodeWasSent() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/sms/send", exchange -> {
            byte[] response = "{\"code\":\"103\",\"message\":\"invalid key\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> sender().send("+821012345678", "인증번호 123456"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
    }

    @Test
    void refusesMissingConfigurationAndLongMessagesBeforeSending() {
        SendmSmsSender unconfigured = new SendmSmsSender("", "", "", HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:1"), json);
        assertThat(unconfigured.available()).isFalse();
        assertThatThrownBy(() -> unconfigured.send("+821012345678", "인증번호 123456"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        SendmSmsSender configured = new SendmSmsSender("account-id", "test-api-key", "01099998888",
                HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1"), json);
        assertThatThrownBy(() -> configured.send("+821012345678", "가".repeat(46)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("short-message limit");
    }

    private SendmSmsSender sender() {
        return new SendmSmsSender("account-id", "test-api-key", "01099998888",
                HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/sms/send"), json);
    }
}
