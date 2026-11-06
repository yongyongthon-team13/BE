package com.yongyong.backend.appointment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}/visit-reviews")
@RequiredArgsConstructor
public class VisitReviewController {

    public record VisitReviewBody(@NotNull VisitReview.Transport transport, @NotNull Boolean onTime) {}

    public record VisitReviewResponse(UUID reviewId, VisitReview.Transport transport, boolean onTime,
                                      OffsetDateTime reviewedAt) {}

    private final AppointmentService appointmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitReviewResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID appointmentId,
                                      @Valid @RequestBody VisitReviewBody body) {
        VisitReview saved = appointmentService.createVisitReview(jwt, appointmentId, body.transport(), body.onTime());
        return new VisitReviewResponse(saved.getId(), saved.getTransport(), saved.isOnTime(), saved.getReviewedAt());
    }
}
