package com.yongyong.backend.appointment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
            select a from Appointment a
            where a.elderId = :elderId
              and (a.appointmentAt between :from and :to
                   or (a.appointmentAt is null and a.appointmentDate between :fromDate and :toDate))
            """)
    List<Appointment> findInRange(@Param("elderId") UUID elderId, @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to, @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate);

    @Query("""
            select a from Appointment a
            where a.elderId = :elderId
              and (a.appointmentAt >= :from or (a.appointmentAt is null and a.appointmentDate >= :fromDate))
            """)
    List<Appointment> findUpcoming(@Param("elderId") UUID elderId, @Param("from") OffsetDateTime from,
                                   @Param("fromDate") LocalDate fromDate);

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
