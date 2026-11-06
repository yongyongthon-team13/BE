package com.yongyong.backend.appointment;

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
@Table(name = "visit_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitReview {

    public enum Transport { BUS, COMPANION, TAXI, GUARDIAN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "review_id", length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "appointment_id", nullable = false, unique = true, length = 36)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Transport transport;

    @Column(name = "on_time", nullable = false)
    private boolean onTime;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    public VisitReview(UUID appointmentId, Transport transport, boolean onTime) {
        this.appointmentId = appointmentId;
        this.transport = transport;
        this.onTime = onTime;
        this.reviewedAt = OffsetDateTime.now();
    }
}
