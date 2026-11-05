package com.yongyong.backend.trip;

import com.yongyong.backend.api.ApiEnvelope;
import com.yongyong.backend.trip.TripDtos.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class TripController {
    private final TripService trips;
    public TripController(TripService trips) { this.trips = trips; }

    @PostMapping("/trips")
    public ResponseEntity<ApiEnvelope<Started>> start(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(trips.start(jwt, request)));
    }
    @GetMapping("/trips/{tripId}")
    public ApiEnvelope<TripStatus> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String tripId) {
        return ApiEnvelope.success(trips.get(jwt, tripId));
    }
    @GetMapping("/guardian/elder-profiles/{elderId}/active-trip")
    public ApiEnvelope<TripStatus> activeTrip(@AuthenticationPrincipal Jwt jwt, @PathVariable String elderId) {
        return ApiEnvelope.success(trips.activeTripForGuardian(jwt, elderId));
    }
    @PostMapping("/trips/{tripId}/location")
    public ApiEnvelope<LocationResult> location(@AuthenticationPrincipal Jwt jwt, @PathVariable String tripId,
            @Valid @RequestBody LocationRequest request) {
        return ApiEnvelope.success(trips.updateLocation(jwt, tripId, request));
    }
    @PostMapping("/trips/{tripId}/events")
    public ApiEnvelope<EventResult> event(@AuthenticationPrincipal Jwt jwt, @PathVariable String tripId,
            @Valid @RequestBody EventRequest request) {
        return ApiEnvelope.success(trips.event(jwt, tripId, request));
    }
    @PostMapping("/trips/{tripId}/check-ins")
    public ApiEnvelope<EventResult> checkIn(@AuthenticationPrincipal Jwt jwt, @PathVariable String tripId,
            @Valid @RequestBody CheckInRequest request) {
        return ApiEnvelope.success(trips.checkIn(jwt, tripId, request));
    }
    @PostMapping("/guardian/elder-profiles/{elderId}/trips/{tripId}/contact-actions")
    public ApiEnvelope<ContactActionResult> contactAction(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String elderId, @PathVariable String tripId,
            @Valid @RequestBody ContactActionRequest request) {
        return ApiEnvelope.success(trips.contactAction(jwt, elderId, tripId, request));
    }
}
