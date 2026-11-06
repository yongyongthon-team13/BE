package com.yongyong.backend.companion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanionRequestRepository extends JpaRepository<CompanionRequest, UUID> {

    boolean existsByAppointmentIdAndStatusIn(UUID appointmentId, List<CompanionRequest.Status> statuses);

    Optional<CompanionRequest> findFirstByAppointmentIdOrderByRequestedAtDesc(UUID appointmentId);

    List<CompanionRequest> findByAppointmentIdOrderByRequestedAtDesc(UUID appointmentId);
}
