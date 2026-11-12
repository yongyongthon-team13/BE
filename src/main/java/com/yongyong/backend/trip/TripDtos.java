package com.yongyong.backend.trip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
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
    public record EventRequest(@NotBlank @Pattern(regexp = "BOARDED|ALIGHTED|ARRIVED|HELP_REQUESTED|CANCELLED|ABORTED")
                               @Schema(allowableValues = {"BOARDED", "ALIGHTED", "ARRIVED", "HELP_REQUESTED", "CANCELLED", "ABORTED"})
                               String eventType,
                               @Schema(description = "선택 이벤트 상세 코드. 이벤트별 자유 문자열이며 현재 허용 목록은 없습니다.", example = "BUS")
                               String detailCode) {}
    public record EventResult(String eventId,
                              @Schema(allowableValues = {"BOARDED", "ALIGHTED", "ARRIVED", "HELP_REQUESTED", "CANCELLED", "ABORTED", "CHECK_IN_RESPONDED"})
                              String eventType,
                              Instant occurredAt,
                              @Schema(allowableValues = {"ACTIVE", "HELP_REQUESTED", "ARRIVED", "CLOSED"}) String status) {}
    public record CheckInRequest(@NotBlank @Pattern(regexp = "OK|NEED_HELP")
                                 @Schema(allowableValues = {"OK", "NEED_HELP"}) String response) {}
    public record TripStatus(String tripId, String routePlanId,
                             @Schema(allowableValues = {"ACTIVE", "HELP_REQUESTED", "ARRIVED", "CLOSED"}) String status,
                             Instant plannedDepartureAt, Instant plannedArrivalAt,
                             Instant estimatedArrivalAt, Instant startedAt, Instant endedAt,
                             Instant lastLocationAt,
                             Double lastLatitude, Double lastLongitude,
                             @Schema(description = "최근 이동 이벤트. 종료 원인을 구분할 때 사용합니다.",
                                     allowableValues = {"DEPARTED", "BOARDED", "ALIGHTED", "ARRIVED", "HELP_REQUESTED", "CANCELLED", "ABORTED", "CHECK_IN_RESPONDED"})
                             String lastEventType) {}
    public record ContactActionRequest(@NotBlank @Pattern(regexp = "CALL|MESSAGE|EMERGENCY_CALL")
                                       @Schema(allowableValues = {"CALL", "MESSAGE", "EMERGENCY_CALL"}) String actionType,
                                       @Pattern(regexp = "CONNECTED|NO_ANSWER|FAILED|OTHER")
                                       @Schema(allowableValues = {"CONNECTED", "NO_ANSWER", "FAILED", "OTHER"}) String outcome,
                                       @Schema(description = "서버 수신 시각을 기록합니다. 입력값은 호환성을 위해 받지만 무시됩니다.", deprecated = true)
                                       Instant occurredAt) {}
    public record ContactActionResult(String contactActionId, String tripId,
                                      @Schema(allowableValues = {"CALL", "MESSAGE", "EMERGENCY_CALL"}) String actionType,
                                      @Schema(allowableValues = {"CONNECTED", "NO_ANSWER", "FAILED", "OTHER"}) String outcome,
                                      Instant occurredAt) {}
}
