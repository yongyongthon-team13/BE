package com.yongyong.backend.appointment.extraction.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ExtractedAppointment(
        Type type,
        String clinicName,
        String department,
        String purpose,
        OffsetDateTime appointmentAt,
        OffsetDateTime previousAt,
        String location,
        List<String> preparations,
        boolean needsGuardian,
        boolean needsReview,
        BigDecimal confidence) {

    public enum Type { NEW, CHANGE, CANCEL }

    public ExtractedAppointment markedForReview() {
        return new ExtractedAppointment(type, clinicName, department, purpose, appointmentAt, previousAt, location,
                preparations, needsGuardian, true, confidence);
    }
}
