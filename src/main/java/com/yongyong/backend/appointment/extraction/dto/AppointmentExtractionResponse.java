package com.yongyong.backend.appointment.extraction.dto;

import java.util.List;

public record AppointmentExtractionResponse(List<ExtractedAppointment> items) {
}
