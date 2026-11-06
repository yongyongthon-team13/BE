package com.yongyong.backend.appointment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitReviewRepository extends JpaRepository<VisitReview, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);
}
