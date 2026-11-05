package com.yongyong.backend.place;

import com.yongyong.backend.place.PlaceDtos.Location;
import com.yongyong.backend.place.PlaceDtos.Place;
import com.yongyong.backend.place.PlaceDtos.SearchResult;
import java.net.http.HttpClient;
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
import tools.jackson.databind.JsonNode;

@Component
public class TmapPlaceClient {
    private static final String BASE_URL = "https://apis.openapi.sk.com";

    private final RestClient restClient;
    private final String appKey;

    @Autowired
    public TmapPlaceClient(@Value("${providers.tmap.app-key:}") String appKey) {
        this(appKey, createRestClient());
    }

    TmapPlaceClient(String appKey, RestClient restClient) {
        this.restClient = restClient;
        this.appKey = appKey == null ? "" : appKey.trim();
    }

    private static RestClient createRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    public SearchResult search(String query, int limit) {
        if (appKey.isBlank()) {
            throw new PlaceApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED",
                    "장소 검색 서비스를 사용할 수 없습니다.");
        }

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/tmap/pois")
                            .queryParam("version", "1")
                            .queryParam("searchKeyword", query)
                            .queryParam("searchType", "all")
                            .queryParam("page", "1")
                            .queryParam("count", Integer.toString(limit))
                            .queryParam("reqCoordType", "WGS84GEO")
                            .queryParam("resCoordType", "WGS84GEO")
                            .queryParam("searchtypCd", "A")
                            .queryParam("multiPoint", "N")
                            .queryParam("poiGroupYn", "N")
                            .build())
                    .header("appKey", appKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode placeNodes = response == null ? null : response.path("searchPoiInfo").path("pois").path("poi");
            List<Place> places = parsePlaces(placeNodes);
            int total = response == null
                    ? places.size()
                    : response.path("searchPoiInfo").path("totalCount").asInt(places.size());
            return new SearchResult(total, places);
        } catch (PlaceApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PlaceApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE",
                    "장소 검색 서비스 응답을 받을 수 없습니다.");
        }
    }

    private List<Place> parsePlaces(JsonNode nodes) {
        List<Place> places = new ArrayList<>();
        if (nodes == null || nodes.isMissingNode() || nodes.isNull()) return places;

        Iterable<JsonNode> items = nodes.isArray() ? nodes : List.of(nodes);
        for (JsonNode item : items) {
            double latitude = number(item, "frontLat", "noorLat");
            double longitude = number(item, "frontLon", "noorLon");
            if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                    || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) continue;

            String placeId = text(item, "id", "pkey");
            String name = text(item, "name");
            if (name.isBlank()) name = "장소 이름 없음";
            String address = String.join(" ", List.of(
                    text(item, "upperAddrName"), text(item, "middleAddrName"),
                    text(item, "lowerAddrName"), text(item, "roadName"),
                    text(item, "firstBuildNo"), secondBuildNo(item))
                    .stream().filter(value -> !value.isBlank()).toList());
            String category = String.join(" > ", List.of(
                    text(item, "upperBizName"), text(item, "middleBizName"),
                    text(item, "lowerBizName"))
                    .stream().filter(value -> !value.isBlank()).toList());

            places.add(new Place(placeId, name, address, category,
                    new Location(latitude, longitude)));
        }
        return places;
    }

    private String secondBuildNo(JsonNode item) {
        String value = text(item, "secondBuildNo");
        return "0".equals(value) ? "" : value;
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private double number(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) return value.asDouble(Double.NaN);
            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Try the fallback coordinate field.
                }
            }
        }
        return Double.NaN;
    }
}
