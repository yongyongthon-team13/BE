package com.yongyong.backend.route;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class RouteDtos {
    private RouteDtos() {}

    public record Point(@NotBlank @Size(max = 255) String name,
                        @Min(-90) @Max(90) double latitude,
                        @Min(-180) @Max(180) double longitude) {}

    public record SearchRequest(@Valid @NotNull Point origin,
                                @Valid @NotNull Point destination,
                                @NotBlank @Pattern(regexp = "WALK|TRANSIT")
                                @Schema(description = "검색할 전체 경로 종류", allowableValues = {"WALK", "TRANSIT"})
                                String mode,
                                Instant departureAt,
                                @Min(1) @Max(5) Integer count) {}
    public record RecommendationRequest(@NotBlank String elderId,
                                        @Valid @NotNull SearchRequest routeSearch) {}

    public record Summary(int durationSeconds, int distanceMeters, int walkingSeconds,
                          int transferCount, int fareWon) {}
    public record TransitRoute(String routeId, String routeName, String routeColor) {}
    public record Stop(String stopId, String name, Double latitude, Double longitude) {}
    public record Arrival(Integer estimatedArrivalSeconds, String vehicleType) {}
    public record StopArrival(String stopId, String stopName, String routeId, String routeName,
                              String status, List<Arrival> arrivals, Instant fetchedAt) {}
    public record Step(String instruction, int distanceMeters, int durationSeconds) {}
    public record Leg(int sequence,
                      @Schema(description = "구간 이동수단", allowableValues = {"WALK", "BUS", "SUBWAY"}) String mode,
                      Point start, Point end,
                      int durationSeconds, int distanceMeters,
                      List<List<Double>> geometry, List<Step> steps,
                      TransitRoute route, List<Stop> stops) {}
    public record RoutePlan(String routePlanId, String provider,
                            @Schema(description = "전체 경로 종류", allowableValues = {"WALK", "TRANSIT"}) String mode,
                            Instant fetchedAt, Instant expiresAt, Summary summary,
                            List<Leg> legs, List<StopArrival> stopArrivals) {}
    public record SearchResult(Instant fetchedAt, Instant expiresAt, List<RoutePlan> alternatives) {}
    public record RecommendationResponse(String status, String recommendedRoutePlanId,
                                         String explanation, List<String> reasonCodes,
                                         List<String> limitations, SearchResult routeSearch) {}
}
