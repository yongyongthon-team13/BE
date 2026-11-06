package com.yongyong.backend.appointment.dto;

import com.yongyong.backend.appointment.Appointment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AppointmentUpdateRequest(
        @Size(min = 1, max = 255) String clinicName,
        Appointment.ClinicSize clinicSize,
        String clinicAddress,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal clinicLat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal clinicLon,
        OffsetDateTime appointmentAt,
        @Size(max = 100) String department,
        @Size(max = 100) String purpose,
        @Size(max = 255) String location,
        @Size(max = 20) List<@Size(max = 200) @Pattern(regexp = "[^\\n]*") String> preparations,
        Boolean needsGuardian,
        Appointment.Status status) {
}
