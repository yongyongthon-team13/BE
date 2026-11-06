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
    public AppointmentResponse create(@Valid @RequestBody AppointmentCreateRequest request) {
        return appointmentService.create(request);
    }

    @GetMapping
    public List<AppointmentResponse> list(
            @RequestParam UUID elderId,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to) {
        return appointmentService.list(elderId, from, to);
    }

    @GetMapping("/{appointmentId}")
    public AppointmentResponse get(@PathVariable UUID appointmentId) {
        return appointmentService.get(appointmentId);
    }

    @PatchMapping("/{appointmentId}")
    public AppointmentResponse update(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody AppointmentUpdateRequest request) {
        return appointmentService.update(appointmentId, request);
    }
}
