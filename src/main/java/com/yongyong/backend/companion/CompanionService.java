package com.yongyong.backend.companion;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.appointment.Appointment;
import com.yongyong.backend.appointment.AppointmentService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter SCRIPT_TIME =
            DateTimeFormatter.ofPattern("M월 d일 EEEE a h시 m분", Locale.KOREAN);

    // 실제 업체와 연동되지 않은 시연용 고정값
    private static final String SERVICE_NAME = "고령 어르신 동행 서비스";
    private static final int ESTIMATED_COST_WON = 20000;
    private static final int PICKUP_MINUTES_BEFORE = 90;
    private static final String PICKUP_PLACE = "집 앞";
    private static final String MANAGER_NAME = "동행 매니저";

    public record AssessmentResponse(UUID assessmentId, boolean recommended, List<String> reasonCodes,
                                     String reasonText, String serviceName, int estimatedCostWon, boolean demo,
                                     String rulesetVersion, OffsetDateTime assessedAt) {}

    public record RequestResponse(UUID requestId, UUID appointmentId, String applicantName, String departureAddress,
                                  boolean vehicleNeeded, String providerCode,
                                  CompanionRequest.BookingMethod bookingMethod, CompanionRequest.Status status,
                                  String serviceName, String servicePhone, int estimatedCostWon, String reasonText,
                                  String callScript,
                                  OffsetDateTime pickupAt, String pickupPlace, String managerName, boolean demo,
                                  OffsetDateTime requestedAt) {}

    public record DepartureCheckResponse(boolean recommendCar, List<String> reasonCodes) {}

    public enum Condition { GOOD, NORMAL, BAD }

    private final AppointmentService appointmentService;
    private final AccountService accountService;
    private final CompanionAssessmentRepository assessmentRepository;
    private final CompanionRequestRepository requestRepository;

    // 업체 번호가 정해지면 서버 환경변수만 바꾸면 된다
    @Value("${app.companion.service-phone}")
    private String servicePhone;

    @Transactional
    public AssessmentResponse assess(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointmentService.findAccessible(jwt, appointmentId);
        AccountService.Mobility mobility =
                accountService.profile(jwt, appointment.getElderId().toString()).mobility();
        List<String> reasons = reasonsOf(mobility);
        if (appointment.isNeedsGuardian()) reasons.add("ESCORT_REQUIRED_BY_CLINIC");
        CompanionAssessment saved = assessmentRepository.save(
                new CompanionAssessment(appointmentId, RULESET_VERSION, reasons));
        return new AssessmentResponse(saved.getId(), saved.isRecommended(), reasons, reasonText(mobility, appointment),
                SERVICE_NAME, ESTIMATED_COST_WON, true, saved.getRulesetVersion(), saved.getAssessedAt());
    }

    @Transactional
    public RequestResponse request(Jwt jwt, UUID appointmentId, String providerCode,
                                   CompanionRequest.BookingMethod bookingMethod) {
        Appointment appointment = appointmentService.findAccessible(jwt, appointmentId);
        if (appointment.getStatus() != Appointment.Status.CONFIRMED || appointment.getAppointmentAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확정된 일정만 신청할 수 있습니다.");
        }
        // 오늘부터 7일 안 일정은 동행 신청을 받지 않는다
        if (!appointment.getAppointmentDate().isAfter(LocalDate.now(Appointment.KOREA).plusDays(7))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "동행서비스는 진료일 8일 전까지 신청할 수 있습니다.");
        }
        if (requestRepository.existsByAppointmentIdAndStatusIn(appointmentId, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 동행서비스 신청이 있습니다.");
        }
        CompanionRequest saved = requestRepository.save(new CompanionRequest(appointmentId, providerCode,
                bookingMethod == null ? CompanionRequest.BookingMethod.PHONE : bookingMethod));
        return toResponse(jwt, appointment, saved);
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> requests(Jwt jwt, UUID appointmentId) {
        Appointment appointment = appointmentService.findAccessible(jwt, appointmentId);
        return requestRepository.findByAppointmentIdOrderByRequestedAtDesc(appointmentId).stream()
                .map(request -> toResponse(jwt, appointment, request))
                .toList();
    }

    @Transactional
    public RequestResponse updateAfterCall(Jwt jwt, UUID appointmentId, UUID requestId, CompanionRequest.Status status,
                                           OffsetDateTime pickupAt, String pickupPlace, String managerName) {
        Appointment appointment = appointmentService.findAccessible(jwt, appointmentId);
        CompanionRequest request = requestRepository.findById(requestId)
                .filter(found -> found.getAppointmentId().equals(appointmentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청을 찾을 수 없습니다."));
        CompanionRequest.Status next = status == null ? CompanionRequest.Status.CONFIRMED : status;
        if (next == CompanionRequest.Status.CONFIRMED) {
            request.updateAfterCall(next,
                    pickupAt != null ? pickupAt : appointment.getAppointmentAt().minusMinutes(PICKUP_MINUTES_BEFORE),
                    pickupPlace != null ? pickupPlace : PICKUP_PLACE,
                    managerName != null ? managerName : MANAGER_NAME);
        } else {
            request.updateAfterCall(next, pickupAt, pickupPlace, managerName);
        }
        return toResponse(jwt, appointment, request);
    }

    @Transactional(readOnly = true)
    public DepartureCheckResponse departureCheck(Jwt jwt, UUID appointmentId, Condition condition, Boolean usesCane) {
        Appointment appointment = appointmentService.findAccessible(jwt, appointmentId);
        List<String> reasons = reasonsOf(accountService.profile(jwt, appointment.getElderId().toString()).mobility());
        if (condition == Condition.BAD) reasons.add("CONDITION_BAD");
        if (Boolean.TRUE.equals(usesCane) && !reasons.contains("MOBILITY_AID_CANE")) reasons.add("USES_CANE_TODAY");
        return new DepartureCheckResponse(!reasons.isEmpty(), reasons);
    }

    private RequestResponse toResponse(Jwt jwt, Appointment appointment, CompanionRequest request) {
        AccountService.Profile profile = accountService.profile(jwt, appointment.getElderId().toString());
        return new RequestResponse(request.getId(), appointment.getId(), profile.name(), profile.homeAddress(),
                needsVehicle(profile.mobility()), request.getProviderCode(), request.getBookingMethod(),
                request.getStatus(), SERVICE_NAME, servicePhone, ESTIMATED_COST_WON,
                reasonText(profile.mobility(), appointment),
                callScript(appointment, profile), request.getPickupAt(), request.getPickupPlace(),
                request.getManagerName(), true, request.getRequestedAt());
    }

    // 개인정보를 외부로 보내지 않고 항상 같은 형식으로 나오도록 AI 대신 템플릿으로 만든다
    private static String callScript(Appointment appointment, AccountService.Profile profile) {
        StringBuilder script = new StringBuilder("병원 동행 신청하고자 전화드렸습니다. ");
        String time = appointment.getAppointmentAt().atZoneSameInstant(KST).format(SCRIPT_TIME).replace(" 0분", "");
        script.append(time).append(' ').append(appointment.getClinicName());
        if (appointment.getDepartment() != null) script.append(' ').append(appointment.getDepartment());
        script.append("입니다.");
        if (profile.homeAddress() != null) script.append(' ').append(profile.homeAddress()).append("에서 출발합니다.");
        String aid = aidName(profile.mobility().mobilityAid());
        if (aid != null) script.append(' ').append(aid).append("를 써서 차가 필요해요.");
        else if (needsVehicle(profile.mobility())) script.append(" 이동이 어려워 차가 필요해요.");
        return script.toString();
    }

    private static boolean needsVehicle(AccountService.Mobility mobility) {
        return !"NONE".equals(mobility.mobilityAid()) || mobility.busDifficult() || mobility.longWalkDifficult();
    }

    private static String aidName(String mobilityAid) {
        return switch (mobilityAid) {
            case "WHEELCHAIR" -> "휠체어";
            case "WALKER" -> "보행기";
            case "CANE" -> "지팡이";
            default -> null;
        };
    }

    private static List<String> reasonsOf(AccountService.Mobility mobility) {
        List<String> reasons = new ArrayList<>();
        if (!"NONE".equals(mobility.mobilityAid())) reasons.add("MOBILITY_AID_" + mobility.mobilityAid());
        if (mobility.stairsDifficult()) reasons.add("STAIRS_DIFFICULT");
        if (mobility.busDifficult()) reasons.add("BUS_DIFFICULT");
        if (mobility.longWalkDifficult()) reasons.add("LONG_WALK_DIFFICULT");
        return reasons;
    }

    private static String reasonText(AccountService.Mobility mobility, Appointment appointment) {
        List<String> parts = new ArrayList<>();
        String aid = aidName(mobility.mobilityAid());
        if (aid != null) parts.add(aid + " 사용");
        if (mobility.stairsDifficult()) parts.add("계단 이용 어려움");
        if (mobility.busDifficult()) parts.add("버스 이용 어려움");
        if (mobility.longWalkDifficult()) parts.add("오래 걷기 어려움");
        if (appointment.isNeedsGuardian()) parts.add("병원의 보호자 동반 안내");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }
}
