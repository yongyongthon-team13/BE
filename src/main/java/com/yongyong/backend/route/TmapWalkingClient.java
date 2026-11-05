package com.yongyong.backend.route;

import com.yongyong.backend.route.RouteDtos.Leg;
import com.yongyong.backend.route.RouteDtos.Point;
import com.yongyong.backend.route.RouteDtos.Step;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class TmapWalkingClient {
    private final RestClient client;
    private final String appKey;

    public TmapWalkingClient(@Value("${providers.tmap.app-key:}") String appKey) {
        this.appKey = appKey == null ? "" : appKey.trim();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().baseUrl("https://apis.openapi.sk.com")
                .requestFactory(factory).build();
    }

    public WalkResult search(Point origin, Point destination) {
        if (appKey.isBlank()) throw new RouteApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "PROVIDER_NOT_CONFIGURED", "도보 경로 서비스를 사용할 수 없습니다.");
        try {
            JsonNode response = client.post().uri("/tmap/routes/pedestrian?version=1")
                    .header("appKey", appKey).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "startX", origin.longitude(),
                            "startY", origin.latitude(),
                            "endX", destination.longitude(),
                            "endY", destination.latitude(),
                            "startName", origin.name(), "endName", destination.name(),
                            "reqCoordType", "WGS84GEO", "resCoordType", "WGS84GEO",
                            "searchOption", "0", "sort", "index"))
                    .retrieve().body(JsonNode.class);
            return parse(response, origin, destination);
        } catch (RouteApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new RouteApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE",
                    "도보 경로 서비스를 사용할 수 없습니다.");
        }
    }

    private WalkResult parse(JsonNode response, Point origin, Point destination) {
        JsonNode features = response == null ? null : response.path("features");
        if (features == null || !features.isArray()) return new WalkResult(0, 0, List.of());
        int distance = 0;
        int duration = 0;
        List<List<Double>> geometry = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode props = feature.path("properties");
            distance = Math.max(distance, props.path("totalDistance").asInt(0));
            duration = Math.max(duration, props.path("totalTime").asInt(0));
            if ("LineString".equals(feature.path("geometry").path("type").asText())) {
                JsonNode coords = feature.path("geometry").path("coordinates");
                if (coords.isArray()) for (JsonNode pair : coords) {
                    if (pair.isArray() && pair.size() >= 2 && pair.get(0).isNumber() && pair.get(1).isNumber())
                        geometry.add(List.of(pair.get(0).asDouble(), pair.get(1).asDouble()));
                }
            }
            if ("Point".equals(feature.path("geometry").path("type").asText())) {
                String instruction = props.path("description").asText(props.path("name").asText("경로 안내"));
                if (!instruction.isBlank()) steps.add(new Step(instruction,
                        Math.max(0, props.path("distance").asInt(0)),
                        Math.max(0, props.path("time").asInt(0))));
            }
        }
        if (geometry.isEmpty()) throw new RouteApiException(HttpStatus.BAD_GATEWAY,
                "INVALID_PROVIDER_RESPONSE", "도보 경로 결과를 처리할 수 없습니다.");
        Leg leg = new Leg(1, "WALK", origin, destination, duration, distance,
                List.copyOf(geometry), List.copyOf(steps), null, List.of());
        return new WalkResult(distance, duration, List.of(leg));
    }

    public record WalkResult(int distanceMeters, int durationSeconds, List<Leg> legs) {}
}
