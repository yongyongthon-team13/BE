package com.yongyong.backend.companion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "companion_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "assessment_id", length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "appointment_id", nullable = false, length = 36)
    private UUID appointmentId;

    @Column(name = "ruleset_version", nullable = false, length = 50)
    private String rulesetVersion;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "reason_codes")
    private String reasonCodes;

    @Column(name = "assessed_at", nullable = false)
    private OffsetDateTime assessedAt;

    public CompanionAssessment(UUID appointmentId, String rulesetVersion, List<String> reasons) {
        this.appointmentId = appointmentId;
        this.rulesetVersion = rulesetVersion;
        this.recommended = !reasons.isEmpty();
        this.reasonCodes = String.join(",", reasons);
        this.assessedAt = OffsetDateTime.now();
    }
}
