package com.yongyong.backend.appointment.extraction;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class GeminiClient {

    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private static final int ATTEMPTS_PER_MODEL = 2;

    private final RestClient restClient;
    private final List<String> models;
    private final Duration backoff;

    @Autowired
    public GeminiClient(@Value("${gemini.api-key}") String apiKey,
                        @Value("${gemini.model}") String model,
                        @Value("${gemini.fallback-models}") List<String> fallbackModels) {
        this(RestClient.builder().requestFactory(requestFactory()), apiKey, model, fallbackModels, Duration.ofSeconds(1));
    }

    GeminiClient(RestClient.Builder builder, String apiKey, String model, List<String> fallbackModels, Duration backoff) {
        this.restClient = builder
                .baseUrl(URL)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Api-Revision", "2026-05-20")
                .build();
        this.models = new ArrayList<>(List.of(model));
        this.models.addAll(fallbackModels);
        this.backoff = backoff;
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return requestFactory;
    }

    // 무료 등급은 503이 자주 나서 같은 모델 재시도 후 다음 모델로 넘어간다
    public String generateJson(String systemInstruction, Object input, JsonNode schema) {
        RuntimeException lastFailure = null;
        for (String model : models) {
            for (int attempt = 0; attempt < ATTEMPTS_PER_MODEL; attempt++) {
                try {
                    return call(model, systemInstruction, input, schema);
                } catch (HttpServerErrorException.ServiceUnavailable | HttpClientErrorException.TooManyRequests e) {
                    lastFailure = e;
                    pause();
                }
            }
        }
        throw lastFailure;
    }

    private String call(String model, String systemInstruction, Object input, JsonNode schema) {
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

    private void pause() {
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
