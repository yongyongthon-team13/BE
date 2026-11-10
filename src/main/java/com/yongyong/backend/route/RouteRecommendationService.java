package com.yongyong.backend.route;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.route.RouteDtos.Arrival;
import com.yongyong.backend.route.RouteDtos.Leg;
import com.yongyong.backend.route.RouteDtos.RecommendationRequest;
import com.yongyong.backend.route.RouteDtos.RecommendationResponse;
import com.yongyong.backend.route.RouteDtos.RoutePlan;
import com.yongyong.backend.route.RouteDtos.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RouteRecommendationService {
    private static final String SYSTEM_INSTRUCTION = """
            어르신의 보행 보조기구, 계단 이용 어려움, 보행 불편 여부와 대중교통 경로 후보를 비교해 한 경로를 추천한다.
            입력에 포함된 경로 후보와 실시간 도착 차량 정보만 근거로 사용한다. 경로·접근성 정보를 추정하거나 새로 만들지 않는다.
            휠체어 이용자는 저상버스가 확인된 모든 버스 구간 후보를 우선하고, 그 안에서는 총 보행시간이 짧은 경로, 환승이 적은 경로 순으로 고른다. 도착 정보의 저상 여부는 조회 시점의 다음 차량 정보일 뿐 해당 노선 전체나 이후 운행 차량을 보장하지 않는다.
            저상버스 정보가 없으면 저상버스 이용 가능이라고 표현하지 말고 확인 불가로 표시한다.
            계단 정보, 보도 경사, 보도 폭, 승강장 접근성은 입력되지 않았으면 판단하지 않는다.
            지팡이·보행기 또는 계단 어려움·보행 불편이 있으면 총 보행시간이 짧은 경로를 우선하고, 다음으로 환승이 적은 경로를 고른다. 저상 차량 정보가 확인되면 보조기구 사용자에게 긍정 요소로 반영한다.
            이동 보조기구와 계단 어려움, 보행 불편이 모두 없으면 전체 이동시간이 짧은 경로를 우선한다.
            응답은 지정한 JSON 스키마를 따른다. 후보 routePlanId 외의 ID를 반환하지 않는다.
            """;

    private static final String RESPONSE_SCHEMA = """
            {
              "type":"object",
              "properties":{
                "recommendedRoutePlanId":{"type":"string","description":"입력 후보 중 하나의 routePlanId"},
                "status":{"type":"string","enum":["RECOMMENDED","CAUTION","ACCESSIBILITY_UNVERIFIED"]},
                "explanation":{"type":"string"},
                "reasonCodes":{"type":"array","items":{"type":"string","enum":[
                  "LOW_FLOOR_OBSERVED","LOW_FLOOR_UNVERIFIED","MOBILITY_AID","STAIRS_DIFFICULT",
                  "WALKING_DISCOMFORT","LESS_WALKING","FEWER_TRANSFERS","INSUFFICIENT_DATA"]}}
              },
              "required":["recommendedRoutePlanId","status","explanation","reasonCodes"]
            }
            """;

    private final AccountService accounts;
    private final RoutePlanService routePlans;
    private final GeminiRouteClient gemini;
    private final ObjectMapper mapper;

    public RouteRecommendationService(AccountService accounts, RoutePlanService routePlans,
                                      GeminiRouteClient gemini, ObjectMapper mapper) {
        this.accounts = accounts;
        this.routePlans = routePlans;
        this.gemini = gemini;
        this.mapper = mapper;
    }

    public RecommendationResponse recommend(Jwt jwt, RecommendationRequest request) {
        if (!"TRANSIT".equalsIgnoreCase(request.routeSearch().mode()))
            throw new RouteApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "저상버스 기반 추천은 대중교통 경로에서만 제공됩니다.");
        if (!gemini.isConfigured())
            throw new RouteApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                    "경로 추천 AI가 설정되지 않았습니다.");

        accounts.requireConsent(jwt, request.elderId(), "AI_ROUTE_RECOMMENDATION");
        AccountService.Profile profile = accounts.profile(jwt, request.elderId());
        AccountService.Mobility mobility = profile.mobility();
        SearchResult search = routePlans.search(jwt, request.routeSearch());
        if (search.alternatives().isEmpty())
            throw new RouteApiException(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "추천할 대중교통 경로가 없습니다.");

        try {
            JsonNode schema = mapper.readTree(RESPONSE_SCHEMA);
            List<RoutePlan> candidates = search.alternatives();
            if ("WHEELCHAIR".equals(mobility.mobilityAid())) {
                List<RoutePlan> withObservedLowFloor = candidates.stream()
                        .filter(this::allBusLegsHaveObservedLowFloor).toList();
                if (!withObservedLowFloor.isEmpty()) candidates = withObservedLowFloor;
            }
            String input = mapper.writeValueAsString(Map.of(
                    "mobility", Map.of(
                            "mobilityAid", mobility.mobilityAid(),
                            "stairsDifficult", mobility.stairsDifficult(),
                            "walkingDiscomfort", mobility.walkingDiscomfort()),
                    "routeCandidates", candidates.stream().map(this::candidateSummary).toList()));
            String json = gemini.recommend(SYSTEM_INSTRUCTION, input, schema);
            AiRecommendation ai = mapper.readValue(json, AiRecommendation.class);
            if (ai == null || ai.recommendedRoutePlanId() == null || ai.explanation() == null
                    || ai.explanation().isBlank() || ai.reasonCodes() == null
                    || !List.of("RECOMMENDED", "CAUTION", "ACCESSIBILITY_UNVERIFIED").contains(ai.status()))
                throw new RouteApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                        "경로 추천 결과를 처리할 수 없습니다.");
            RoutePlan chosen = search.alternatives().stream()
                    .filter(route -> route.routePlanId().equals(ai.recommendedRoutePlanId()))
                    .findFirst().orElseThrow(() -> new RouteApiException(HttpStatus.BAD_GATEWAY,
                            "AI_INVALID_RESPONSE", "경로 추천 결과를 확인할 수 없습니다."));

            List<String> limitations = new ArrayList<>();
            limitations.add("저상 여부는 조회 시점에 도착 예정으로 확인된 차량 정보이며, 이후 운행 차량은 달라질 수 있습니다.");
            limitations.add("보도 경사·계단·보도 폭·승강장 접근성은 현재 경로 데이터로 확인하지 않습니다.");
            String status = ai.status();
            if ("WHEELCHAIR".equals(mobility.mobilityAid()) && !allBusLegsHaveObservedLowFloor(chosen)) {
                status = "ACCESSIBILITY_UNVERIFIED";
                limitations.add("추천 경로의 모든 버스 구간에서 저상버스 도착 정보를 확인하지 못했습니다.");
            }
            return new RecommendationResponse(status, chosen.routePlanId(), ai.explanation(),
                    ai.reasonCodes(), List.copyOf(limitations), search);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new RouteApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE",
                    "경로 추천 결과를 처리할 수 없습니다.");
        }
    }

    private CandidateSummary candidateSummary(RoutePlan plan) {
        List<BusLegSummary> busLegs = plan.legs().stream().filter(leg -> "BUS".equals(leg.mode()))
                .map(leg -> busSummary(leg, plan.stopArrivals())).toList();
        return new CandidateSummary(plan.routePlanId(), plan.summary().durationSeconds(),
                plan.summary().walkingSeconds(), plan.summary().transferCount(), busLegs);
    }

    private BusLegSummary busSummary(Leg leg, List<RouteDtos.StopArrival> stopArrivals) {
        String routeId = leg.route() == null ? "" : Objects.toString(leg.route().routeId(), "");
        String routeName = leg.route() == null ? "" : Objects.toString(leg.route().routeName(), "");
        RouteDtos.StopArrival arrival = stopArrivals.stream()
                .filter(item -> (!routeId.isBlank() && routeId.equals(item.routeId()))
                        || (!routeName.isBlank() && routeName.equals(item.routeName()))).findFirst().orElse(null);
        List<VehicleSummary> vehicles = arrival == null ? List.of() : arrival.arrivals().stream()
                .map(a -> new VehicleSummary(a.estimatedArrivalSeconds(), a.vehicleType())).toList();
        return new BusLegSummary(arrival == null ? "UNAVAILABLE" : arrival.status(), vehicles);
    }

    private boolean allBusLegsHaveObservedLowFloor(RoutePlan plan) {
        List<Leg> busLegs = plan.legs().stream().filter(leg -> "BUS".equals(leg.mode())).toList();
        if (busLegs.isEmpty()) return false;
        return busLegs.stream().allMatch(leg -> {
            String routeId = leg.route() == null ? "" : Objects.toString(leg.route().routeId(), "");
            String routeName = leg.route() == null ? "" : Objects.toString(leg.route().routeName(), "");
            return plan.stopArrivals().stream()
                    .filter(item -> (!routeId.isBlank() && routeId.equals(item.routeId()))
                            || (!routeName.isBlank() && routeName.equals(item.routeName())))
                    .anyMatch(item -> "AVAILABLE".equals(item.status()) && item.arrivals().stream()
                            .map(Arrival::vehicleType).anyMatch("LOW_FLOOR"::equals));
        });
    }

    private record CandidateSummary(String routePlanId, int totalDurationSeconds, int walkingSeconds,
                                    int transferCount, List<BusLegSummary> busLegs) {}
    private record BusLegSummary(String arrivalStatus, List<VehicleSummary> nextVehicles) {}
    private record VehicleSummary(Integer arrivalSeconds, String vehicleType) {}
    private record AiRecommendation(String recommendedRoutePlanId, String status,
                                    String explanation, List<String> reasonCodes) {}
}
