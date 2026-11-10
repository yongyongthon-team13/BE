package com.yongyong.backend.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.account.AccountService.Mobility;
import com.yongyong.backend.account.AccountService.Profile;
import com.yongyong.backend.route.RouteDtos.Arrival;
import com.yongyong.backend.route.RouteDtos.Leg;
import com.yongyong.backend.route.RouteDtos.Point;
import com.yongyong.backend.route.RouteDtos.RecommendationRequest;
import com.yongyong.backend.route.RouteDtos.RoutePlan;
import com.yongyong.backend.route.RouteDtos.SearchRequest;
import com.yongyong.backend.route.RouteDtos.SearchResult;
import com.yongyong.backend.route.RouteDtos.Stop;
import com.yongyong.backend.route.RouteDtos.StopArrival;
import com.yongyong.backend.route.RouteDtos.Summary;
import com.yongyong.backend.route.RouteDtos.TransitRoute;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

class RouteRecommendationServiceTest {
    private AccountService accounts;
    private RoutePlanService routePlans;
    private GeminiRouteClient gemini;
    private RouteRecommendationService service;
    private Jwt jwt;
    private SearchRequest searchRequest;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountService.class);
        routePlans = mock(RoutePlanService.class);
        gemini = mock(GeminiRouteClient.class);
        service = new RouteRecommendationService(accounts, routePlans, gemini,
                JsonMapper.builder().build());
        jwt = mock(Jwt.class);
        searchRequest = new SearchRequest(new Point("출발지", 37.0, 127.0),
                new Point("목적지", 37.1, 127.1), "TRANSIT", null, 3);
        when(gemini.isConfigured()).thenReturn(true);
    }

    @Test
    void wheelchairRecommendationSendsOnlyCandidatesWithObservedLowFloor() {
        RoutePlan verified = plan("verified", "LOW_FLOOR", "비공개 정류장 이름");
        RoutePlan unverified = plan("unverified", "STANDARD", "다른 정류장 이름");
        when(accounts.profile(jwt, "elder-1")).thenReturn(profile("WHEELCHAIR"));
        when(routePlans.search(jwt, searchRequest)).thenReturn(searchResult(verified, unverified));
        when(gemini.recommend(anyString(), anyString(), any())).thenReturn(aiResponse("verified"));

        var response = service.recommend(jwt, new RecommendationRequest("elder-1", searchRequest));

        assertEquals("verified", response.recommendedRoutePlanId());
        assertEquals("RECOMMENDED", response.status());
        var input = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gemini).recommend(anyString(), input.capture(), any());
        assertEquals(true, input.getValue().contains("verified"));
        assertFalse(input.getValue().contains("unverified"));
        assertFalse(input.getValue().contains("비공개 정류장 이름"));
        assertFalse(input.getValue().contains("37.0"));
    }

    @Test
    void wheelchairRecommendationMarksRouteUnverifiedWhenNoLowFloorWasObserved() {
        RoutePlan unverified = plan("unverified", "STANDARD", "정류장 이름");
        when(accounts.profile(jwt, "elder-1")).thenReturn(profile("WHEELCHAIR"));
        when(routePlans.search(jwt, searchRequest)).thenReturn(searchResult(unverified));
        when(gemini.recommend(anyString(), anyString(), any())).thenReturn(aiResponse("unverified"));

        var response = service.recommend(jwt, new RecommendationRequest("elder-1", searchRequest));

        assertEquals("ACCESSIBILITY_UNVERIFIED", response.status());
        assertEquals("unverified", response.recommendedRoutePlanId());
    }

    private Profile profile(String aid) {
        return new Profile("elder-1", "이름", null, null,
                new Mobility(aid, false, false, false, false), List.of());
    }

    private RoutePlan plan(String id, String vehicleType, String stopName) {
        var busLeg = new Leg(0, "BUS", new Point("출발 정류장", 37.0, 127.0),
                new Point("도착 정류장", 37.01, 127.01), 600, 1000, List.of(), List.of(),
                new TransitRoute("route-" + id, "노선명 " + id, null),
                List.of(new Stop("stop-1", stopName, 37.0, 127.0)));
        var arrival = new StopArrival("stop-1", stopName, "route-" + id, "노선명 " + id,
                "AVAILABLE", List.of(new Arrival(120, vehicleType)), Instant.now());
        return new RoutePlan(id, "TMAP_TRANSIT", "TRANSIT", Instant.now(), null,
                new Summary(1800, 5000, 600, 1, 1500), List.of(busLeg), List.of(arrival));
    }

    private SearchResult searchResult(RoutePlan... plans) {
        return new SearchResult(Instant.now(), null, List.of(plans));
    }

    private String aiResponse(String id) {
        return "{\"recommendedRoutePlanId\":\"" + id
                + "\",\"status\":\"RECOMMENDED\",\"explanation\":\"보행 부담이 적습니다.\""
                + ",\"reasonCodes\":[\"LOW_FLOOR_OBSERVED\"]}";
    }
}
