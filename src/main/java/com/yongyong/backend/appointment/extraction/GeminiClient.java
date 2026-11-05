package com.yongyong.backend.appointment.extraction;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiClient {

    private final RestClient restClient;
    private final String model;

    public GeminiClient(@Value("${gemini.api-key}") String apiKey, @Value("${gemini.model}") String model) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/interactions")
                .requestFactory(requestFactory)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Api-Revision", "2026-05-20")
                .build();
        this.model = model;
    }

    public String generateJson(String systemInstruction, String input, JsonNode schema) {
        JsonNode response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "system_instruction", systemInstruction,
                        "input", input,
                        "response_format", Map.of(
                                "type", "text",
                                "mime_type", "application/json",
                                "schema", schema)))
                .retrieve()
                .body(JsonNode.class);
        JsonNode steps = response.path("steps");
        return steps.path(steps.size() - 1).path("content").path(0).path("text").asString();
    }
}
