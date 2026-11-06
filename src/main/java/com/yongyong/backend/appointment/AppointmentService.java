package com.yongyong.backend.appointment;

import com.yongyong.backend.appointment.dto.AppointmentCreateRequest;
import com.yongyong.backend.appointment.dto.AppointmentResponse;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {
        return AppointmentResponse.from(appointmentRepository.save(new Appointment(request)));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(UUID elderId, OffsetDateTime from, OffsetDateTime to) {
        return appointmentRepository.findByElderIdAndAppointmentAtBetweenOrderByAppointmentAt(elderId, from, to)
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID appointmentId) {
        return AppointmentResponse.from(find(appointmentId));
    }

    @Transactional
    public AppointmentResponse update(UUID appointmentId, AppointmentUpdateRequest request) {
        Appointment appointment = find(appointmentId);
        appointment.update(request);
        return AppointmentResponse.from(appointment);
    }

    private Appointment find(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
    }
}
