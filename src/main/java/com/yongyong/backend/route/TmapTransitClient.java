package com.yongyong.backend.route;

import com.yongyong.backend.route.RouteDtos.*;
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
public class TmapTransitClient {
    private final RestClient client;
    private final String appKey;

    public TmapTransitClient(@Value("${providers.tmap.transit-app-key:${providers.tmap.app-key:}}") String appKey) {
        this.appKey = appKey == null ? "" : appKey.trim();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().baseUrl("https://apis.openapi.sk.com").requestFactory(factory).build();
    }

    public List<TransitAlternative> search(Point origin, Point destination, int count) {
        if (appKey.isBlank()) throw new RouteApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "PROVIDER_NOT_CONFIGURED", "대중교통 경로 서비스를 사용할 수 없습니다.");
        try {
            JsonNode response = client.post().uri("/transit/routes")
                    .header("appKey", appKey).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("startX", Double.toString(origin.longitude()),
                            "startY", Double.toString(origin.latitude()),
                            "endX", Double.toString(destination.longitude()),
                            "endY", Double.toString(destination.latitude()),
                            "count", Math.max(1, Math.min(5, count)), "lang", 0, "format", "json"))
                    .retrieve().body(JsonNode.class);
            JsonNode itineraries = response == null ? null : response.path("metaData").path("plan").path("itineraries");
            if (itineraries == null || !itineraries.isArray()) itineraries = response == null ? null : response.path("plan").path("itineraries");
            if (itineraries == null || !itineraries.isArray()) return List.of();
            List<TransitAlternative> result = new ArrayList<>();
            for (JsonNode itinerary : itineraries) {
                JsonNode legNodes = itinerary.path("legs");
                if (!legNodes.isArray() || legNodes.isEmpty()) continue;
                List<Leg> legs = new ArrayList<>();
                int seq = 1, computedWalk = 0, totalDistance = 0;
                for (JsonNode legNode : legNodes) {
                    String mode = normalizeMode(legNode.path("mode").asText("WALK"));
                    int seconds = nonNegative(legNode.path("sectionTime").asInt(0));
                    int meters = nonNegative(legNode.path("distance").asInt(0));
                    totalDistance += meters;
                    if ("WALK".equals(mode)) computedWalk += seconds;
                    Point start = point(legNode.path("start"), seq == 1 ? origin : null);
                    Point end = point(legNode.path("end"), null);
                    List<List<Double>> geometry = parseLine(legNode.path("passShape").path("linestring").asText(""));
                    List<Step> steps = parseSteps(legNode.path("steps"));
                    JsonNode lane = legNode.path("Lane").isArray() && !legNode.path("Lane").isEmpty()
                            ? legNode.path("Lane").get(0) : null;
                    String routeName = firstText(legNode, "route", lane, "route");
                    String routeId = firstText(legNode, "routeId", lane, "routeId");
                    String routeColor = firstText(legNode, "routeColor", lane, "routeColor");
                    TransitRoute route = routeName.isBlank() && routeId.isBlank() ? null
                            : new TransitRoute(routeId, routeName, routeColor);
                    List<Stop> stops = parseStops(legNode.path("passStopList"));
                    legs.add(new Leg(seq++, mode, start, end, seconds, meters, geometry, steps, route, stops));
                }
                int totalSeconds = nonNegative(itinerary.path("totalTime").asInt(0));
                int walking = nonNegative(itinerary.path("totalWalkTime").asInt(computedWalk));
                int transfers = nonNegative(itinerary.path("transferCount").asInt(0));
                int fare = nonNegative(itinerary.path("fare").path("regular").path("totalFare").asInt(0));
                result.add(new TransitAlternative(totalSeconds, totalDistance, walking, transfers, fare, List.copyOf(legs)));
            }
            return List.copyOf(result);
        } catch (RestClientException exception) {
            throw new RouteApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE",
                    "대중교통 경로 서비스를 사용할 수 없습니다.");
        }
    }

    private static Point point(JsonNode node, Point fallback) {
        String name = node.path("name").asText("");
        double lat = parseDouble(node.path("lat"));
        double lon = parseDouble(node.path("lon"));
        if (name.isBlank() && fallback != null) name = fallback.name();
        if (!Double.isFinite(lat) && fallback != null) lat = fallback.latitude();
        if (!Double.isFinite(lon) && fallback != null) lon = fallback.longitude();
        if (name.isBlank()) name = "경로 지점";
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) return new Point(name, 0, 0);
        return new Point(name, lat, lon);
    }
    private static List<Stop> parseStops(JsonNode passStopList) {
        JsonNode nodes = passStopList.path("stationList");
        if (!nodes.isArray()) nodes = passStopList.path("stations");
        if (nodes.isMissingNode() || nodes.isNull()) return List.of();
        List<Stop> result = new ArrayList<>();
        Iterable<JsonNode> iterable = nodes.isArray() ? nodes : List.of(nodes);
        for (JsonNode node : iterable) {
            String id = node.path("stationID").asText(node.path("stationId").asText(""));
            String name = node.path("stationName").asText("");
            double lat = parseDouble(node.path("lat")), lon = parseDouble(node.path("lon"));
            if (!id.isBlank()) result.add(new Stop(id, name, Double.isFinite(lat) ? lat : null,
                    Double.isFinite(lon) ? lon : null));
        }
        return List.copyOf(result);
    }
    private static List<Step> parseSteps(JsonNode nodes) {
        if (!nodes.isArray()) return List.of();
        List<Step> result = new ArrayList<>();
        for (JsonNode n : nodes) {
            String desc = n.path("description").asText("도보 이동");
            result.add(new Step(desc, nonNegative(n.path("distance").asInt(0)), 0));
        }
        return List.copyOf(result);
    }
    private static List<List<Double>> parseLine(String value) {
        List<List<Double>> result = new ArrayList<>();
        for (String pair : value.trim().split("\\s+")) {
            String[] pieces = pair.split(",");
            if (pieces.length < 2) continue;
            try { result.add(List.of(Double.parseDouble(pieces[0]), Double.parseDouble(pieces[1]))); }
            catch (NumberFormatException ignored) { }
        }
        return List.copyOf(result);
    }
    private static String firstText(JsonNode primary, String primaryField, JsonNode fallback, String fallbackField) {
        String value = primary.path(primaryField).asText("");
        return value.isBlank() && fallback != null ? fallback.path(fallbackField).asText("") : value;
    }
    private static String normalizeMode(String mode) {
        return switch (mode.toUpperCase()) { case "BUS", "SUBWAY", "WALK" -> mode.toUpperCase(); default -> "WALK"; };
    }
    private static double parseDouble(JsonNode node) {
        if (node.isNumber()) return node.asDouble(Double.NaN);
        try { return Double.parseDouble(node.asText()); } catch (Exception ignored) { return Double.NaN; }
    }
    private static int nonNegative(int value) { return Math.max(0, value); }
    public record TransitAlternative(int durationSeconds, int distanceMeters, int walkingSeconds,
                                     int transferCount, int fareWon, List<Leg> legs) {}
}
