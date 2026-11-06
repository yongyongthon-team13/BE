package com.yongyong.backend.appointment.dto;

import com.yongyong.backend.appointment.Appointment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AppointmentUpdateRequest(
        @Size(min = 1, max = 255) String clinicName,
        String clinicAddress,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal clinicLat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal clinicLon,
        OffsetDateTime appointmentAt,
        @Size(max = 100) String department,
        Appointment.Status status) {
}
