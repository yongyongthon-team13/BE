package com.yongyong.backend.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Primary
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "sendm")
public class SendmSmsSender extends SmsSender {
    private static final URI SEND_URL = URI.create("https://api.sendm.co.kr/v2/sms/send");
    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    private final String userId;
    private final String apiKey;
    private final String sender;
    private final HttpClient client;
    private final URI endpoint;
    private final ObjectMapper json;

    @Autowired
    public SendmSmsSender(@Value("${app.sms.sendm.user-id:}") String userId,
                          @Value("${app.sms.sendm.api-key:}") String apiKey,
                          @Value("${app.sms.sendm.sender:}") String sender) {
        this(userId, apiKey, sender,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                SEND_URL, new ObjectMapper());
    }

    SendmSmsSender(String userId, String apiKey, String sender,
                   HttpClient client, URI endpoint, ObjectMapper json) {
        this.userId = userId;
        this.apiKey = apiKey;
        this.sender = sender;
        this.client = client;
        this.endpoint = endpoint;
        this.json = json;
    }

    @Override
    public boolean available() {
        return userId != null && !userId.isBlank() && apiKey != null && !apiKey.isBlank()
                && sender != null && sender.matches("0[0-9]{9,10}");
    }

    @Override
    public void send(String phoneNumber, String message) {
        if (!available())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider is not configured");
        if (message == null || !EUC_KR.newEncoder().canEncode(message)
                || message.getBytes(EUC_KR).length > 90)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS message exceeds the short-message limit");
        if (phoneNumber == null || !phoneNumber.matches("\\+8210[0-9]{8}"))
            throw new IllegalArgumentException("Expected a normalized Korean mobile number");

        String localNumber = "0" + phoneNumber.substring(3);
        try {
            String body = json.writeValueAsString(Map.of(
                    "callerNo", sender,
                    "receiveNos", localNumber,
                    "message", message,
                    "smsType", "SMS",
                    "adYn", "N"));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("user-id", userId)
                    .header("api-key", apiKey)
                    .header("request-id", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider rejected the request");
            JsonNode result = json.readTree(response.body());
            if (result == null || !"0".equals(result.path("code").asText()))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider rejected the request");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider is unavailable");
        }
    }
}
