package com.yongyong.backend.appointment;

import com.yongyong.backend.appointment.dto.AppointmentCreateRequest;
import com.yongyong.backend.appointment.dto.AppointmentResponse;
import com.yongyong.backend.appointment.dto.AppointmentUpdateRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AppointmentCreateRequest request) {
        return appointmentService.create(jwt, request);
    }

    @GetMapping
    public List<AppointmentResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID elderId,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to) {
        return appointmentService.list(jwt, elderId, from, to);
    }

    @GetMapping("/{appointmentId}")
    public AppointmentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID appointmentId) {
        return appointmentService.get(jwt, appointmentId);
    }

    @PatchMapping("/{appointmentId}")
    public AppointmentResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentUpdateRequest request) {
        return appointmentService.update(jwt, appointmentId, request);
    }
}
