package com.yongyong.backend.appointment.dto;

import com.yongyong.backend.appointment.Appointment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AppointmentResponse(
        UUID appointmentId,
        UUID elderId,
        String clinicName,
        String clinicAddress,
        BigDecimal clinicLat,
        BigDecimal clinicLon,
        OffsetDateTime appointmentAt,
        String department,
        String purpose,
        String location,
        List<String> preparations,
        boolean needsGuardian,
        Appointment.Source source,
        Appointment.Status status,
        BigDecimal extractionConfidence,
        OffsetDateTime confirmedAt) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getElderId(),
                appointment.getClinicName(),
                appointment.getClinicAddress(),
                appointment.getClinicLat(),
                appointment.getClinicLon(),
                appointment.getAppointmentAt(),
                appointment.getDepartment(),
                appointment.getPurpose(),
                appointment.getLocation(),
                appointment.getPreparationList(),
                appointment.isNeedsGuardian(),
                appointment.getSource(),
                appointment.getStatus(),
                appointment.getExtractionConfidence(),
                appointment.getConfirmedAt());
    }
}
