package com.yongyong.backend.companion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/appointments/{appointmentId}")
@RequiredArgsConstructor
public class CompanionController {

    public record CompanionRequestBody(@Size(max = 100) String providerCode,
                                       CompanionRequest.BookingMethod bookingMethod) {}

    private final CompanionService companionService;

    @PostMapping("/companion-assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanionService.AssessmentResponse assess(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID appointmentId) {
        return companionService.assess(jwt, appointmentId);
    }

    @PostMapping("/companion-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanionService.RequestResponse request(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID appointmentId,
                                                    @Valid @RequestBody CompanionRequestBody body) {
        return companionService.request(jwt, appointmentId, body.providerCode(), body.bookingMethod());
    }
}
