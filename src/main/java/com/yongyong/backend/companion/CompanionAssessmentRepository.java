package com.yongyong.backend.companion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionAssessmentRepository extends JpaRepository<CompanionAssessment, UUID> {
}
