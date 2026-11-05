package com.yongyong.backend.companion;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.appointment.Appointment;
import com.yongyong.backend.appointment.AppointmentService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
public class CompanionService {

    private static final String RULESET_VERSION = "mobility-v1";
    private static final List<CompanionRequest.Status> ACTIVE_STATUSES = List.of(
            CompanionRequest.Status.PENDING, CompanionRequest.Status.CONTACTED, CompanionRequest.Status.CONFIRMED);

    public record AssessmentResponse(UUID assessmentId, boolean recommended, List<String> reasonCodes,
                                     String rulesetVersion, OffsetDateTime assessedAt) {}

    public record RequestResponse(UUID requestId, UUID appointmentId, String providerCode,
                                  CompanionRequest.BookingMethod bookingMethod, CompanionRequest.Status status,
                                  OffsetDateTime requestedAt) {}

    private final AppointmentService appointmentService;
    private final AccountService accountService;
    private final CompanionAssessmentRepository assessmentRepository;
    private final CompanionRequestRepository requestRepository;

    @Transactional
    public AssessmentResponse assess(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointmentService.findAccessible(jwt, appointmentId);
        AccountService.Mobility mobility =
                accountService.profile(jwt, appointment.getElderId().toString()).mobility();
        List<String> reasons = reasonsOf(mobility);
        if (appointment.isNeedsGuardian()) reasons.add("ESCORT_REQUIRED_BY_CLINIC");
        CompanionAssessment saved = assessmentRepository.save(
                new CompanionAssessment(appointmentId, RULESET_VERSION, reasons));
        return new AssessmentResponse(saved.getId(), saved.isRecommended(), reasons,
                saved.getRulesetVersion(), saved.getAssessedAt());
    }

    @Transactional
    public RequestResponse request(Jwt jwt, UUID appointmentId, String providerCode,
                                   CompanionRequest.BookingMethod bookingMethod) {
        appointmentService.findAccessible(jwt, appointmentId);
        if (requestRepository.existsByAppointmentIdAndStatusIn(appointmentId, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 동행서비스 신청이 있습니다.");
        }
        CompanionRequest saved = requestRepository.save(new CompanionRequest(appointmentId, providerCode,
                bookingMethod == null ? CompanionRequest.BookingMethod.PHONE : bookingMethod));
        return new RequestResponse(saved.getId(), appointmentId, saved.getProviderCode(),
                saved.getBookingMethod(), saved.getStatus(), saved.getRequestedAt());
    }

    private static List<String> reasonsOf(AccountService.Mobility mobility) {
        List<String> reasons = new ArrayList<>();
        if (!"NONE".equals(mobility.mobilityAid())) reasons.add("MOBILITY_AID_" + mobility.mobilityAid());
        if (mobility.stairsDifficult()) reasons.add("STAIRS_DIFFICULT");
        if (mobility.busDifficult()) reasons.add("BUS_DIFFICULT");
        if (mobility.longWalkDifficult()) reasons.add("LONG_WALK_DIFFICULT");
        return reasons;
    }
}
