package com.yongyong.backend.guardian;

import com.yongyong.backend.account.AccountService;
import com.yongyong.backend.account.DataCipher;
import com.yongyong.backend.appointment.Appointment;
import com.yongyong.backend.appointment.AppointmentRepository;
import com.yongyong.backend.appointment.AppointmentService;
import com.yongyong.backend.appointment.dto.AppointmentResponse;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GuardianAppointmentService {

    public record ElderSummary(String elderId, String name, Integer birthYear, String relationship, String phoneNumber) {}

    public record PendingTasks(List<AppointmentResponse> helpRequests, List<AppointmentResponse> visitReviews) {}

    private record Link(String elderId, String relationship) {}

    private final AccountService accountService;
    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final JdbcTemplate db;
    private final DataCipher cipher;

    @Transactional(readOnly = true)
    public List<ElderSummary> elders(Jwt jwt) {
        AccountService.User guardian = requireGuardian(jwt);
        List<Link> links = db.query(
                "SELECT elder_id, relationship FROM guardian_links WHERE guardian_user_id = ? AND status = 'ACTIVE'",
                (rs, n) -> new Link(rs.getString(1), rs.getString(2)), guardian.userId());
        List<ElderSummary> elders = new ArrayList<>();
        for (Link link : links) {
            try {
                AccountService.Profile profile = accountService.profile(jwt, link.elderId());
                // 어르신이 로그인할 때 인증한 번호, 그 전에 가입했으면 null
                String phone = db.queryForObject("""
                        SELECT u.phone_number_enc FROM elder_profiles e JOIN app_users u ON u.user_id = e.user_id
                        WHERE e.elder_id = ?""", String.class, link.elderId());
                elders.add(new ElderSummary(profile.elderId(), profile.name(), profile.birthYear(),
                        link.relationship(), cipher.decrypt(phone)));
            } catch (ResponseStatusException e) {
                // 어르신이 공유 동의를 철회하면 profile이 404를 던지니까 목록에서 뺀다
                if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) throw e;
            }
        }
        return elders;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> upcomingAppointments(Jwt jwt, UUID elderId) {
        requireGuardian(jwt);
        accountService.profile(jwt, elderId.toString());
        OffsetDateTime now = OffsetDateTime.now();
        return appointmentRepository.findUpcoming(elderId, now, now.atZoneSameInstant(Appointment.KOREA).toLocalDate())
                .stream()
                .sorted(Appointment.BY_SCHEDULE)
                .map(appointmentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PendingTasks pendingTasks(Jwt jwt, UUID elderId) {
        requireGuardian(jwt);
        accountService.profile(jwt, elderId.toString());
        return new PendingTasks(
                appointmentRepository.findByElderIdAndHelpRequestedAtIsNotNullOrderByHelpRequestedAt(elderId).stream()
                        .map(appointmentService::toResponse).toList(),
                appointmentRepository.findUnreviewedVisits(elderId, OffsetDateTime.now()).stream()
                        .map(appointmentService::toResponse).toList());
    }

    @Transactional
    public AppointmentResponse assist(Jwt jwt, UUID elderId, UUID appointmentId, AppointmentUpdateRequest request) {
        requireGuardian(jwt);
        Appointment appointment = appointmentService.findAccessible(jwt, appointmentId);
        if (!appointment.getElderId().equals(elderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다.");
        }
        appointment.assistByGuardian(request);
        return appointmentService.toResponse(appointment);
    }

    private AccountService.User requireGuardian(Jwt jwt) {
        AccountService.User user = accountService.current(jwt);
        if (!"GUARDIAN".equals(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "보호자 계정만 사용할 수 있습니다.");
        }
        return user;
    }
}
