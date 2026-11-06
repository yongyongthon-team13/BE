package com.yongyong.backend.appointment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByElderIdAndAppointmentAtBetweenOrderByAppointmentAt(
            UUID elderId, OffsetDateTime from, OffsetDateTime to);

    List<Appointment> findByElderIdAndAppointmentAtGreaterThanEqualOrderByAppointmentAt(
            UUID elderId, OffsetDateTime from);

    List<Appointment> findByElderIdAndHelpRequestedAtIsNotNullOrderByHelpRequestedAt(UUID elderId);

    @Query("""
            select a from Appointment a
            where a.elderId = :elderId and a.status = com.yongyong.backend.appointment.Appointment.Status.CONFIRMED
              and a.appointmentAt < :now
              and not exists (select r.id from VisitReview r where r.appointmentId = a.id)
            order by a.appointmentAt desc
            """)
    List<Appointment> findUnreviewedVisits(@Param("elderId") UUID elderId, @Param("now") OffsetDateTime now);
}
