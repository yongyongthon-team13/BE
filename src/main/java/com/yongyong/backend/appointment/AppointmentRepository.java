package com.yongyong.backend.appointment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByElderIdAndAppointmentAtBetweenOrderByAppointmentAt(
            UUID elderId, OffsetDateTime from, OffsetDateTime to);

    List<Appointment> findByElderIdAndAppointmentAtGreaterThanEqualOrderByAppointmentAt(
            UUID elderId, OffsetDateTime from);
}
