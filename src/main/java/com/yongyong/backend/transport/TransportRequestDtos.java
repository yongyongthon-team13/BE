package com.yongyong.backend.transport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class TransportRequestDtos {
    private TransportRequestDtos() {}
    public enum Type { TAXI, CAREGIVER_PICKUP, ACCESSIBLE_TRANSPORT, GUARDIAN_ASSISTANCE }
    public enum GuardianResponse { PICKUP_BY_GUARDIAN, CALL_TAXI }
    public record PickupLocation(@NotBlank @Size(max = 255) String name,
                                 @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                 @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
    public record CreateRequest(@NotNull Type type, @Valid PickupLocation pickupLocation,
                                @Size(max = 500) String note) {}
    public record RespondRequest(@NotNull GuardianResponse response) {}
    public record TransportRequest(String requestId, Type type, String status, GuardianResponse guardianResponse,
                                   Instant requestedAt, Instant updatedAt,
                                   String statusDescription) {}
}
