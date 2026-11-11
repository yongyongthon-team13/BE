package com.yongyong.backend.route;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiRouteClient {
    private final RestClient client;
    private final String apiKey;
    private final String model;

    public GeminiRouteClient(@Value("${gemini.api-key:}") String apiKey,
                             @Value("${gemini.model:gemini-3.5-flash-lite}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/interactions")
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", this.apiKey)
                .defaultHeader("Api-Revision", "2026-05-20")
                .build();
    }

    public boolean isConfigured() { return !apiKey.isBlank(); }

    public String recommend(String instruction, String input, JsonNode schema) {
        try {
            JsonNode response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "store", false,
                            "system_instruction", instruction,
                            "input", input,
                            "response_format", Map.of("type", "text", "mime_type", "application/json", "schema", schema)))
                    .retrieve().body(JsonNode.class);
            JsonNode steps = response == null ? null : response.path("steps");
            if (steps == null || !steps.isArray() || steps.isEmpty())
                throw new RouteApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                        "경로 추천 결과를 처리할 수 없습니다.");
            String text = steps.get(steps.size() - 1).path("content").path(0).path("text").asString();
            if (text == null || text.isBlank()) throw new RouteApiException(HttpStatus.BAD_GATEWAY,
                    "AI_INVALID_RESPONSE", "경로 추천 결과를 처리할 수 없습니다.");
            return text;
        } catch (RestClientException exception) {
            throw new RouteApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE", "경로 추천을 생성할 수 없습니다.");
        }
    }
}
