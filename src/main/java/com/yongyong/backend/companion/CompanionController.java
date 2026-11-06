package com.yongyong.backend.companion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}")
@RequiredArgsConstructor
public class CompanionController {

    public record CompanionRequestBody(@Size(max = 100) String providerCode,
                                       CompanionRequest.BookingMethod bookingMethod) {}

    public record CallResultBody(CompanionRequest.Status status, OffsetDateTime pickupAt,
                                 @Size(max = 255) String pickupPlace, @Size(max = 100) String managerName) {}

    public record DepartureCheckBody(CompanionService.Condition condition, Boolean usesCane) {}

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

    @GetMapping("/companion-requests")
    public List<CompanionService.RequestResponse> requests(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID appointmentId) {
        return companionService.requests(jwt, appointmentId);
    }

    @PatchMapping("/companion-requests/{requestId}")
    public CompanionService.RequestResponse updateAfterCall(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID appointmentId,
                                                            @PathVariable UUID requestId,
                                                            @Valid @RequestBody CallResultBody body) {
        return companionService.updateAfterCall(jwt, appointmentId, requestId, body.status(), body.pickupAt(),
                body.pickupPlace(), body.managerName());
    }

    @PostMapping("/departure-checks")
    public CompanionService.DepartureCheckResponse departureCheck(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable UUID appointmentId,
                                                                  @RequestBody DepartureCheckBody body) {
        return companionService.departureCheck(jwt, appointmentId, body.condition(), body.usesCane());
    }
}
