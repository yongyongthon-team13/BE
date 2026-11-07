package com.yongyong.backend.appointment.extraction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ExtractedAppointment(
        Type type,
        String clinicName,
        String clinicSize,
        String department,
        String purpose,
        LocalDate appointmentDate,
        OffsetDateTime appointmentAt,
        OffsetDateTime previousAt,
        String location,
        List<String> preparations,
        boolean needsGuardian,
        boolean needsReview,
        BigDecimal confidence) {

    public enum Type { NEW, CHANGE, CANCEL }

    public ExtractedAppointment withClinicSize(String size) {
        return new ExtractedAppointment(type, clinicName, size, department, purpose, appointmentDate, appointmentAt, previousAt,
                location, preparations, needsGuardian, needsReview, confidence);
    }

    public ExtractedAppointment withSchedule(LocalDate date, OffsetDateTime at) {
        return new ExtractedAppointment(type, clinicName, clinicSize, department, purpose, date, at, previousAt,
                location, preparations, needsGuardian, needsReview, confidence);
    }

    public ExtractedAppointment markedForReview() {
        return new ExtractedAppointment(type, clinicName, clinicSize, department, purpose, appointmentDate, appointmentAt, previousAt, location,
                preparations, needsGuardian, true, confidence);
    }
}
