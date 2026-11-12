package com.yongyong.backend.account;

import tools.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TmapGeocodingClient {
    private static final String BASE_URL = "https://apis.openapi.sk.com";
    private final RestClient restClient;
    private final String appKey;

    @Autowired
    public TmapGeocodingClient(@Value("${providers.tmap.app-key:}") String appKey) {
        this(appKey, createRestClient());
    }

    TmapGeocodingClient(String appKey, RestClient restClient) {
        this.appKey = appKey == null ? "" : appKey.trim();
        this.restClient = restClient;
    }

    private static RestClient createRestClient() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        return RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
    }

    public List<HomeLocationDtos.Candidate> search(String address) {
        if (appKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TMAP 주소 검색을 사용할 수 없습니다.");
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/tmap/geo/fullAddrGeo")
                            .queryParam("version", "1")
                            .queryParam("addressFlag", "F00")
                            .queryParam("coordType", "WGS84GEO")
                            .queryParam("fullAddr", address)
                            .queryParam("page", "1")
                            .queryParam("count", "10")
                            .build())
                    .header("appKey", appKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            return parseCandidates(response == null ? null
                    : response.path("coordinateInfo").path("coordinate"));
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TMAP 주소 검색 응답을 받을 수 없습니다.");
        }
    }

    private List<HomeLocationDtos.Candidate> parseCandidates(JsonNode nodes) {
        if (nodes == null || nodes.isMissingNode() || nodes.isNull()) return List.of();
        Iterable<JsonNode> items = nodes.isArray() ? nodes : List.of(nodes);
        List<HomeLocationDtos.Candidate> candidates = new ArrayList<>();
        for (JsonNode item : items) {
            double[] point = point(item);
            if (point == null) continue;
            String address = String.join(" ", List.of(
                    text(item, "city_do"), text(item, "gu_gun"), text(item, "eup_myun"),
                    text(item, "newRoadName"), text(item, "newBuildingIndex"))
                    .stream().filter(value -> !value.isBlank()).toList());
            candidates.add(new HomeLocationDtos.Candidate(address,
                    firstText(item, "newBuildingName", "buildingName"),
                    firstText(item, "newBuildingDong", "buildingDong"), point[0], point[1]));
            if (candidates.size() == 10) break;
        }
        return List.copyOf(candidates);
    }

    private double[] point(JsonNode item) {
        double lat = number(item, "newLatEntr");
        double lon = number(item, "newLonEntr");
        if (!valid(lat, lon)) {
            lat = number(item, "newLat");
            lon = number(item, "newLon");
        }
        return valid(lat, lon) ? new double[]{lat, lon} : null;
    }

    private boolean valid(double latitude, double longitude) {
        return Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    }

    private double number(JsonNode item, String field) {
        JsonNode value = item.path(field);
        if (value.isNumber()) return value.asDouble(Double.NaN);
        try { return Double.parseDouble(value.asText()); }
        catch (RuntimeException ignored) { return Double.NaN; }
    }

    private String firstText(JsonNode item, String first, String second) {
        String value = text(item, first);
        return value.isBlank() ? text(item, second) : value;
    }

    private String text(JsonNode item, String field) {
        return item.path(field).asText("").trim();
    }
}
