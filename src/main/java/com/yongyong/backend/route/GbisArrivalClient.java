package com.yongyong.backend.route;

import com.yongyong.backend.route.RouteDtos.Arrival;
import com.yongyong.backend.route.RouteDtos.Leg;
import com.yongyong.backend.route.RouteDtos.RoutePlan;
import com.yongyong.backend.route.RouteDtos.Stop;
import com.yongyong.backend.route.RouteDtos.StopArrival;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class GbisArrivalClient {
    private static final String BASE_URL = "https://apis.data.go.kr/6410000";
    private final RestClient client;
    private final String serviceKey;

    public GbisArrivalClient(@Value("${providers.gbis.service-key:}") String serviceKey) {
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(12));
        this.client = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
    }

    public List<StopArrival> refresh(RoutePlan plan) {
        List<StopArrival> result = new ArrayList<>();
        for (Leg leg : plan.legs()) {
            if (!"BUS".equals(leg.mode()) || leg.route() == null) continue;
            result.add(refreshBusLeg(leg));
        }
        return List.copyOf(result);
    }

    public List<StopArrival> unavailable(RoutePlan plan) {
        List<StopArrival> result = new ArrayList<>();
        for (Leg leg : plan.legs()) if ("BUS".equals(leg.mode()) && leg.route() != null) {
            String stopId = leg.stops().isEmpty() ? "" : leg.stops().get(0).stopId();
            result.add(new StopArrival(stopId, leg.start().name(), leg.route().routeId(),
                    leg.route().routeName(), "UNAVAILABLE", List.of(), null));
        }
        return List.copyOf(result);
    }

    private StopArrival refreshBusLeg(Leg leg) {
        String routeId = leg.route().routeId();
        String routeName = leg.route().routeName();
        Instant fetchedAt = Instant.now();
        String fallbackStopId = leg.stops().isEmpty() ? "" : leg.stops().get(0).stopId();
        if (serviceKey.isBlank()) return new StopArrival(fallbackStopId, leg.start().name(), routeId,
                routeName, "UNAVAILABLE", List.of(), fetchedAt);
        try {
            JsonNode around = client.get().uri(uri -> uri.path("/busstationservice/v2/getBusStationAroundListv2")
                            .queryParam("x", leg.start().longitude()).queryParam("y", leg.start().latitude())
                            .queryParam("format", "json").queryParam("serviceKey", serviceKey).build())
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
            List<StopCandidate> nearby = stationRows(around).stream().map(this::candidate)
                    .filter(s -> !s.id().isBlank()).toList();
            List<StopCandidate> exact = nearby.stream()
                    .filter(s -> normalize(s.name()).equals(normalize(leg.start().name())))
                    .sorted(Comparator.comparingDouble(StopCandidate::distance)).toList();
            List<StopCandidate> candidates;
            if (!exact.isEmpty()) candidates = exact.stream().limit(2).toList();
            else {
                List<StopCandidate> close = nearby.stream().filter(s -> s.distance() >= 0 && s.distance() <= 30)
                        .sorted(Comparator.comparingDouble(StopCandidate::distance)).toList();
                // Proximity alone is accepted only when there is a single nearby candidate.
                candidates = close.size() == 1 ? close : List.of();
            }
            for (StopCandidate candidate : candidates) {
                JsonNode arrivalsResponse = client.get().uri(uri -> uri.path("/busarrivalservice/v2/getBusArrivalListv2")
                                .queryParam("stationId", candidate.id()).queryParam("format", "json")
                                .queryParam("serviceKey", serviceKey).build())
                        .accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
                List<Arrival> arrivals = arrivalRows(arrivalsResponse).stream()
                        .filter(a -> normalizeRouteName(a.path("routeName").asText())
                                .equals(normalizeRouteName(routeName)))
                        .map(a -> arrival(a)).filter(a -> a.estimatedArrivalSeconds() != null).toList();
                if (!arrivals.isEmpty()) return new StopArrival(candidate.id(), candidate.name(), routeId,
                        routeName, "AVAILABLE", arrivals, Instant.now());
            }
            return new StopArrival(fallbackStopId, leg.start().name(), routeId, routeName,
                    "NOT_FOUND", List.of(), Instant.now());
        } catch (RestClientException exception) {
            return new StopArrival(fallbackStopId, leg.start().name(), routeId, routeName,
                    "UNAVAILABLE", List.of(), Instant.now());
        }
    }

    private List<JsonNode> stationRows(JsonNode response) {
        return rows(response == null ? null : response.path("busStationList"),
                response == null ? null : response.path("busStationAroundList"),
                response == null ? null : response.path("response").path("msgBody").path("busStationAroundList"),
                response == null ? null : response.path("response").path("body").path("items").path("item"));
    }
    private List<JsonNode> arrivalRows(JsonNode response) {
        return rows(response == null ? null : response.path("busArrivalList"),
                response == null ? null : response.path("response").path("msgBody").path("busArrivalList"),
                response == null ? null : response.path("response").path("body").path("items").path("item"));
    }
    private List<JsonNode> rows(JsonNode... candidates) {
        JsonNode node = null;
        for (JsonNode candidate : candidates) if (candidate != null && !candidate.isMissingNode() && !candidate.isNull()) {
            node = candidate; break;
        }
        if (node == null) return List.of();
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) return List.of(node);
        List<JsonNode> list = new ArrayList<>(); node.forEach(list::add); return list;
    }
    private StopCandidate candidate(JsonNode node) {
        return new StopCandidate(node.path("stationId").asText(""), node.path("stationName").asText(""),
                node.path("distance").asDouble(-1));
    }
    private Arrival arrival(JsonNode node) {
        int minutes = node.path("predictTime1").asInt(-1);
        if (minutes < 0) minutes = node.path("predictTime2").asInt(-1);
        return new Arrival(minutes < 0 ? null : minutes * 60, "UNKNOWN");
    }
    private static String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(); }
    private static String normalizeRouteName(String value) {
        String normalized = normalize(value);
        int separator = normalized.lastIndexOf(':');
        if (separator >= 0) normalized = normalized.substring(separator + 1);
        return normalized.replaceFirst("^(일반|마을|간선|지선|광역|급행|좌석|직행좌석)", "");
    }
    private record StopCandidate(String id, String name, double distance) {}
}
