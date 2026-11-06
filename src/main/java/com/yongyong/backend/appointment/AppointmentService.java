package com.yongyong.backend.appointment;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.appointment.dto.AppointmentCreateRequest;
import com.yongyong.backend.appointment.dto.AppointmentResponse;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AccountService accountService;

    @Transactional
    public AppointmentResponse create(Jwt jwt, AppointmentCreateRequest request) {
        accountService.profile(jwt, request.elderId().toString());
        return AppointmentResponse.from(appointmentRepository.save(new Appointment(request)));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(Jwt jwt, UUID elderId, OffsetDateTime from, OffsetDateTime to) {
        accountService.profile(jwt, elderId.toString());
        return appointmentRepository.findByElderIdAndAppointmentAtBetweenOrderByAppointmentAt(elderId, from, to)
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Jwt jwt, UUID appointmentId) {
        return AppointmentResponse.from(findAccessible(jwt, appointmentId));
    }

    @Transactional
    public AppointmentResponse update(Jwt jwt, UUID appointmentId, AppointmentUpdateRequest request) {
        Appointment appointment = findAccessible(jwt, appointmentId);
        appointment.update(request);
        return AppointmentResponse.from(appointment);
    }

    public Appointment findAccessible(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
        accountService.profile(jwt, appointment.getElderId().toString());
        return appointment;
    }
}
