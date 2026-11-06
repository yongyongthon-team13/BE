package com.yongyong.backend.appointment.dto;

import com.yongyong.backend.appointment.Appointment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AppointmentCreateRequest(
        @NotNull UUID elderId,
        @NotBlank @Size(max = 255) String clinicName,
        String clinicAddress,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal clinicLat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal clinicLon,
        @NotNull OffsetDateTime appointmentAt,
        @Size(max = 100) String department,
        @Size(max = 100) String purpose,
        @Size(max = 255) String location,
        @Size(max = 20) List<@Size(max = 200) @Pattern(regexp = "[^\\n]*") String> preparations,
        Boolean needsGuardian,
        @NotNull Appointment.Source source,
        @DecimalMin("0") @DecimalMax("1") BigDecimal extractionConfidence) {
}
