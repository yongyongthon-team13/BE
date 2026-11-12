package com.yongyong.backend.account;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class HomeLocationDtos {
    private HomeLocationDtos() {}

    public record Candidate(String address, String buildingName, String buildingDong,
                            double latitude, double longitude) {}

    public record SearchResponse(List<Candidate> candidates) {}

    public record SaveRequest(@NotBlank @Size(max = 500) String address,
                              @Size(max = 200) String addressDetail,
                              @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
                              @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {}

    public record HomeLocation(String address, Double latitude, Double longitude, Instant verifiedAt) {}
}
