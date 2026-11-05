package com.yongyong.backend.place;

import com.yongyong.backend.api.ApiEnvelope;
import com.yongyong.backend.place.PlaceDtos.SearchResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/places")
@SecurityRequirement(name = "bearerAuth")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<SearchResult>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return ResponseEntity.ok(ApiEnvelope.success(placeService.search(jwt, q, limit)));
    }
}
