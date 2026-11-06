package com.yongyong.backend.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class TripDtos {
    private TripDtos() {}
    public record StartRequest(@NotBlank String routePlanId, Instant plannedDepartureAt) {}
    public record Started(String tripId, String routePlanId, String status,
                          Instant plannedDepartureAt, Instant startedAt, Instant estimatedArrivalAt) {}
    public record LocationRequest(@Min(-90) @Max(90) double latitude,
                                  @Min(-180) @Max(180) double longitude,
                                  @Min(0) @Max(1000) int accuracyMeters,
                                  @NotNull Instant recordedAt) {}
    public record LocationResult(String status, Instant recordedAt, Instant expiresAt,
                                 Instant estimatedArrivalAt, boolean delayed) {}
    public record EventRequest(@NotBlank String eventType, String detailCode) {}
    public record EventResult(String eventId, String eventType, Instant occurredAt, String status) {}
    public record CheckInRequest(@NotBlank String response) {}
    public record TripStatus(String tripId, String routePlanId, String status,
                             Instant plannedDepartureAt, Instant plannedArrivalAt,
                             Instant estimatedArrivalAt, Instant startedAt, Instant endedAt,
                             Instant lastLocationAt) {}
    public record ContactActionRequest(@NotBlank String actionType, String outcome, Instant occurredAt) {}
    public record ContactActionResult(String contactActionId, String tripId,
                                      String actionType, String outcome, Instant occurredAt) {}
}
