package com.yongyong.backend.transport;

import com.yongyong.backend.api.ApiEnvelope;
import com.yongyong.backend.transport.TransportRequestDtos.CreateRequest;
import com.yongyong.backend.transport.TransportRequestDtos.TransportRequest;
import com.yongyong.backend.transport.TransportRequestDtos.RespondRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}/transport-requests")
@SecurityRequirement(name = "bearerAuth")
public class TransportRequestController {
    private final TransportRequestService requests;
    public TransportRequestController(TransportRequestService requests) { this.requests = requests; }

    @PostMapping
    public ResponseEntity<ApiEnvelope<TransportRequest>> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId, @Valid @RequestBody CreateRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
                requests.create(jwt, appointmentId, request, idempotencyKey)));
    }
    @GetMapping
    public ApiEnvelope<List<TransportRequest>> list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId) {
        return ApiEnvelope.success(requests.list(jwt, appointmentId));
    }

    @GetMapping("/{requestId}")
    public ApiEnvelope<TransportRequest> get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId, @PathVariable UUID requestId) {
        return ApiEnvelope.success(requests.get(jwt, appointmentId, requestId));
    }

    @PostMapping("/{requestId}/response")
    public ApiEnvelope<TransportRequest> respond(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId, @PathVariable UUID requestId,
            @Valid @RequestBody RespondRequest request) {
        return ApiEnvelope.success(requests.respond(jwt, appointmentId, requestId, request));
    }
}
