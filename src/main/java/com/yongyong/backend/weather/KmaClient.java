package com.yongyong.backend.weather;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class KmaClient {

    record ForecastItem(String category, String fcstDate, String fcstTime, String fcstValue) {
    }

    private static final String NO_DATA = "03";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String serviceKey;

    public KmaClient(JsonMapper jsonMapper, @Value("${kma.service-key}") String serviceKey) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl("https://apis.data.go.kr/1360000")
                .requestFactory(requestFactory)
                .build();
        this.jsonMapper = jsonMapper;
        this.serviceKey = serviceKey;
    }

    public List<ForecastItem> getForecast(String baseDate, String baseTime, int nx, int ny) {
        String body = restClient.get()
                .uri(uri -> uri.path("/VilageFcstInfoService_2.0/getVilageFcst")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("dataType", "JSON")
                        .queryParam("numOfRows", 1000)
                        .queryParam("pageNo", 1)
                        .queryParam("base_date", baseDate)
                        .queryParam("base_time", baseTime)
                        .queryParam("nx", nx)
                        .queryParam("ny", ny)
                        .build())
                .retrieve()
                .body(String.class);
        List<ForecastItem> items = new ArrayList<>();
        for (JsonNode item : response(body, false).path("body").path("items").path("item")) {
            items.add(new ForecastItem(
                    item.path("category").asString(),
                    item.path("fcstDate").asString(),
                    item.path("fcstTime").asString(),
                    item.path("fcstValue").asString()));
        }
        return items;
    }

    public String getCurrentWarnings() {
        String body = restClient.get()
                .uri(uri -> uri.path("/WthrWrnInfoService/getPwnStatus")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("dataType", "JSON")
                        .queryParam("numOfRows", 1)
                        .queryParam("pageNo", 1)
                        .build())
                .retrieve()
                .body(String.class);
        return response(body, true).path("body").path("items").path("item").path(0).path("t6").asString("");
    }

    private JsonNode response(String body, boolean noDataAllowed) {
        JsonNode response = jsonMapper.readTree(body).path("response");
        String resultCode = response.path("header").path("resultCode").asString();
        if (!"00".equals(resultCode) && !(noDataAllowed && NO_DATA.equals(resultCode))) {
            throw new IllegalStateException("기상청 응답 코드 " + resultCode);
        }
        return response;
    }
}
