package com.yongyong.backend.appointment;

import com.yongyong.backend.appointment.dto.AppointmentCreateRequest;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "appointments", indexes = @Index(columnList = "elder_id, appointment_at, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {

    public enum Source { SMS, MANUAL, GUARDIAN }

    public enum Status { DRAFT, CONFIRMED, CANCELLED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "appointment_id")
    private UUID id;

    @Column(name = "elder_id", nullable = false)
    private UUID elderId;

    @Column(name = "clinic_name", nullable = false)
    private String clinicName;

    @Column(name = "clinic_address_enc", columnDefinition = "text")
    private String clinicAddress;

    @Column(name = "clinic_lat", precision = 9, scale = 6)
    private BigDecimal clinicLat;

    @Column(name = "clinic_lon", precision = 9, scale = 6)
    private BigDecimal clinicLon;

    @Column(name = "appointment_at", nullable = false)
    private OffsetDateTime appointmentAt;

    @Column(length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "extraction_confidence", precision = 4, scale = 3)
    private BigDecimal extractionConfidence;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Appointment(AppointmentCreateRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        this.elderId = request.elderId();
        this.clinicName = request.clinicName();
        this.clinicAddress = request.clinicAddress();
        this.clinicLat = request.clinicLat();
        this.clinicLon = request.clinicLon();
        this.appointmentAt = request.appointmentAt();
        this.department = request.department();
        this.source = request.source();
        this.status = Status.CONFIRMED;
        this.extractionConfidence = request.extractionConfidence();
        this.confirmedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(AppointmentUpdateRequest request) {
        if (request.clinicName() != null) clinicName = request.clinicName();
        if (request.clinicAddress() != null) clinicAddress = request.clinicAddress();
        if (request.clinicLat() != null) clinicLat = request.clinicLat();
        if (request.clinicLon() != null) clinicLon = request.clinicLon();
        if (request.appointmentAt() != null) appointmentAt = request.appointmentAt();
        if (request.department() != null) department = request.department();
        if (request.status() != null) status = request.status();
        updatedAt = OffsetDateTime.now();
    }
}
