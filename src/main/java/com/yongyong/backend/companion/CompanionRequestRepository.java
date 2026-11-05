package com.yongyong.backend.companion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionRequestRepository extends JpaRepository<CompanionRequest, UUID> {

    boolean existsByAppointmentIdAndStatusIn(UUID appointmentId, List<CompanionRequest.Status> statuses);
}
