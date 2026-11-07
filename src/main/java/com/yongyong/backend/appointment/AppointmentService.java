package com.yongyong.backend.appointment;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.appointment.dto.AppointmentCreateRequest;
import com.yongyong.backend.appointment.dto.AppointmentResponse;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import com.yongyong.backend.companion.CompanionRequest;
import com.yongyong.backend.companion.CompanionRequestRepository;
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
    private final CompanionRequestRepository companionRequestRepository;
    private final VisitReviewRepository visitReviewRepository;

    @Transactional
    public AppointmentResponse create(Jwt jwt, AppointmentCreateRequest request) {
        accountService.profile(jwt, request.elderId().toString());
        boolean complete = request.clinicName() != null && !request.clinicName().isBlank()
                && request.appointmentAt() != null;
        if (!complete && !Boolean.TRUE.equals(request.helpRequested())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "병원명과 시간이 필요합니다.");
        }
        return toResponse(appointmentRepository.save(new Appointment(request)));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(Jwt jwt, UUID elderId, OffsetDateTime from, OffsetDateTime to) {
        accountService.profile(jwt, elderId.toString());
        return appointmentRepository.findInRange(elderId, from, to,
                        from.atZoneSameInstant(Appointment.KOREA).toLocalDate(), to.atZoneSameInstant(Appointment.KOREA).toLocalDate())
                .stream()
                .sorted(Appointment.BY_SCHEDULE)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Jwt jwt, UUID appointmentId) {
        return toResponse(findAccessible(jwt, appointmentId));
    }

    @Transactional
    public AppointmentResponse update(Jwt jwt, UUID appointmentId, AppointmentUpdateRequest request) {
        Appointment appointment = findAccessible(jwt, appointmentId);
        appointment.update(request);
        return toResponse(appointment);
    }

    @Transactional
    public VisitReview createVisitReview(Jwt jwt, UUID appointmentId, VisitReview.Transport transport, boolean onTime) {
        Appointment appointment = findAccessible(jwt, appointmentId);
        if (visitReviewRepository.existsByAppointmentId(appointmentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 작성한 기록이 있습니다.");
        }
        VisitReview review = visitReviewRepository.save(new VisitReview(appointmentId, transport, onTime));
        appointment.complete();
        return review;
    }

    public AppointmentResponse toResponse(Appointment appointment) {
        AppointmentResponse.CompanionSummary companion = appointment.getId() == null ? null : companionRequestRepository
                .findFirstByAppointmentIdOrderByRequestedAtDesc(appointment.getId())
                .filter(request -> request.getStatus() != CompanionRequest.Status.CANCELLED
                        && request.getStatus() != CompanionRequest.Status.REJECTED)
                .map(request -> new AppointmentResponse.CompanionSummary(request.getStatus().name(),
                        request.getPickupAt(), request.getPickupPlace(), request.getManagerName()))
                .orElse(null);
        return AppointmentResponse.from(appointment, companion);
    }

    public Appointment findAccessible(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
        accountService.profile(jwt, appointment.getElderId().toString());
        return appointment;
    }
}
