package com.yongyong.backend.appointment.extraction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record AppointmentExtractionRequest(
        @NotBlank @Size(max = 2000) String text,
        OffsetDateTime receivedAt) {
}
