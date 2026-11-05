package com.yongyong.backend.companion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "companion_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionRequest {

    public enum BookingMethod { PHONE, WEB, PARTNER_API }

    public enum Status { PENDING, CONTACTED, CONFIRMED, REJECTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_id", length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "appointment_id", nullable = false, length = 36)
    private UUID appointmentId;

    @Column(name = "provider_code", length = 100)
    private String providerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_method", nullable = false, length = 30)
    private BookingMethod bookingMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    public CompanionRequest(UUID appointmentId, String providerCode, BookingMethod bookingMethod) {
        this.appointmentId = appointmentId;
        this.providerCode = providerCode;
        this.bookingMethod = bookingMethod;
        this.status = Status.PENDING;
        this.requestedAt = OffsetDateTime.now();
    }
}
