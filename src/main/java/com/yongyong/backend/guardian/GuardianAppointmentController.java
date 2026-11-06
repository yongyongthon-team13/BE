package com.yongyong.backend.guardian;

import com.yongyong.backend.appointment.dto.AppointmentResponse;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guardian/elder-profiles")
@RequiredArgsConstructor
public class GuardianAppointmentController {

    private final GuardianAppointmentService guardianAppointmentService;

    @GetMapping
    public List<GuardianAppointmentService.ElderSummary> elders(@AuthenticationPrincipal Jwt jwt) {
        return guardianAppointmentService.elders(jwt);
    }

    @GetMapping("/{elderId}/appointments")
    public List<AppointmentResponse> appointments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID elderId) {
        return guardianAppointmentService.upcomingAppointments(jwt, elderId);
    }

    @GetMapping("/{elderId}/pending-tasks")
    public GuardianAppointmentService.PendingTasks pendingTasks(@AuthenticationPrincipal Jwt jwt,
                                                                @PathVariable UUID elderId) {
        return guardianAppointmentService.pendingTasks(jwt, elderId);
    }

    @PostMapping("/{elderId}/appointments/{appointmentId}/assist")
    public AppointmentResponse assist(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID elderId,
                                      @PathVariable UUID appointmentId,
                                      @Valid @RequestBody AppointmentUpdateRequest request) {
        return guardianAppointmentService.assist(jwt, elderId, appointmentId, request);
    }
}
