package com.yongyong.backend.appointment;

import com.yongyong.backend.appointment.dto.AppointmentCreateRequest;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "appointments", indexes = @Index(columnList = "elder_id, appointment_at, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {

    public enum Source { SMS, MANUAL, GUARDIAN }

    public enum Status { DRAFT, CONFIRMED, CANCELLED, COMPLETED }

    public enum ClinicSize { SMALL, MEDIUM, LARGE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "appointment_id", length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "elder_id", nullable = false, length = 36)
    private UUID elderId;

    @Column(name = "clinic_name")
    private String clinicName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "clinic_size", length = 10)
    private ClinicSize clinicSize;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "clinic_address_enc", columnDefinition = "text")
    private String clinicAddress;

    @Column(name = "clinic_lat", precision = 9, scale = 6)
    private BigDecimal clinicLat;

    @Column(name = "clinic_lon", precision = 9, scale = 6)
    private BigDecimal clinicLon;

    @Column(name = "appointment_at")
    private OffsetDateTime appointmentAt;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String purpose;

    private String location;

    @Column(columnDefinition = "text")
    private String preparations;

    @Column(name = "needs_guardian", nullable = false)
    private boolean needsGuardian;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Source source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "extraction_confidence", precision = 4, scale = 3)
    private BigDecimal extractionConfidence;

    @Column(name = "help_requested_at")
    private OffsetDateTime helpRequestedAt;

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
        this.clinicSize = request.clinicSize();
        this.clinicAddress = request.clinicAddress();
        this.clinicLat = request.clinicLat();
        this.clinicLon = request.clinicLon();
        this.appointmentAt = request.appointmentAt();
        this.department = request.department();
        this.purpose = request.purpose();
        this.location = request.location();
        this.preparations = join(request.preparations());
        this.needsGuardian = Boolean.TRUE.equals(request.needsGuardian());
        this.source = request.source();
        boolean helpRequested = Boolean.TRUE.equals(request.helpRequested());
        this.status = helpRequested ? Status.DRAFT : Status.CONFIRMED;
        this.helpRequestedAt = helpRequested ? now : null;
        this.extractionConfidence = request.extractionConfidence();
        this.confirmedAt = helpRequested ? null : now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(AppointmentUpdateRequest request) {
        if (request.clinicName() != null) clinicName = request.clinicName();
        if (request.clinicSize() != null) clinicSize = request.clinicSize();
        if (request.clinicAddress() != null) clinicAddress = request.clinicAddress();
        if (request.clinicLat() != null) clinicLat = request.clinicLat();
        if (request.clinicLon() != null) clinicLon = request.clinicLon();
        if (request.appointmentAt() != null) appointmentAt = request.appointmentAt();
        if (request.department() != null) department = request.department();
        if (request.purpose() != null) purpose = request.purpose();
        if (request.location() != null) location = request.location();
        if (request.preparations() != null) preparations = join(request.preparations());
        if (request.needsGuardian() != null) needsGuardian = request.needsGuardian();
        if (request.status() != null) status = request.status();
        updatedAt = OffsetDateTime.now();
        if (status == Status.DRAFT && clinicName != null && appointmentAt != null) {
            status = Status.CONFIRMED;
            confirmedAt = updatedAt;
            helpRequestedAt = null;
        }
    }

    public List<String> getPreparationList() {
        return preparations == null || preparations.isEmpty() ? List.of() : List.of(preparations.split("\n"));
    }

    private static String join(List<String> items) {
        return items == null ? null : String.join("\n", items);
    }

    public void complete() {
        status = Status.COMPLETED;
        updatedAt = OffsetDateTime.now();
    }

    public void assistByGuardian(AppointmentUpdateRequest request) {
        update(request);
        source = Source.GUARDIAN;
    }
}
