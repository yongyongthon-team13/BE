package com.yongyong.backend.appointment.dto;

import com.yongyong.backend.appointment.Appointment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentCreateRequest(
        @NotNull UUID elderId,
        @NotBlank @Size(max = 255) String clinicName,
        String clinicAddress,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal clinicLat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal clinicLon,
        @NotNull OffsetDateTime appointmentAt,
        @Size(max = 100) String department,
        @NotNull Appointment.Source source,
        @DecimalMin("0") @DecimalMax("1") BigDecimal extractionConfidence) {
}
