package com.yongyong.backend.route;

import com.yongyong.backend.api.ApiEnvelope;
import com.yongyong.backend.route.RouteDtos.RoutePlan;
import com.yongyong.backend.route.RouteDtos.RecommendationRequest;
import com.yongyong.backend.route.RouteDtos.RecommendationResponse;
import com.yongyong.backend.route.RouteDtos.SearchRequest;
import com.yongyong.backend.route.RouteDtos.SearchResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/route-plans")
@SecurityRequirement(name = "bearerAuth")
public class RoutePlanController {
    private final RoutePlanService routePlans;
    private final RouteRecommendationService recommendations;
    public RoutePlanController(RoutePlanService routePlans, RouteRecommendationService recommendations) {
        this.routePlans = routePlans;
        this.recommendations = recommendations;
    }

    @PostMapping("/search")
    public ResponseEntity<ApiEnvelope<SearchResult>> search(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SearchRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(routePlans.search(jwt, request)));
    }

    @PostMapping("/recommendations")
    public ResponseEntity<ApiEnvelope<RecommendationResponse>> recommend(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecommendationRequest request) {
        return ResponseEntity.ok(ApiEnvelope.success(recommendations.recommend(jwt, request)));
    }

    @GetMapping("/{routePlanId}")
    public ApiEnvelope<RoutePlan> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String routePlanId,
            @RequestParam(defaultValue = "false") boolean refreshArrivals) {
        return ApiEnvelope.success(routePlans.get(jwt, routePlanId, refreshArrivals));
    }
}
