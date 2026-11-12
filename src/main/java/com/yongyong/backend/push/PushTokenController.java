package com.yongyong.backend.push;

import com.yongyong.backend.api.ApiEnvelope;
import com.yongyong.backend.push.PushTokenDtos.Register;
import com.yongyong.backend.push.PushTokenDtos.Registered;
import com.yongyong.backend.push.PushTokenDtos.Unregister;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-tokens")
@SecurityRequirement(name = "bearerAuth")
public class PushTokenController {
    private final PushTokenService tokens;
    public PushTokenController(PushTokenService tokens) { this.tokens = tokens; }

    @PostMapping
    public ApiEnvelope<Registered> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Register request) {
        tokens.register(jwt, request);
        return ApiEnvelope.success(new Registered(true));
    }

    @DeleteMapping
    public ApiEnvelope<Registered> unregister(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Unregister request) {
        tokens.unregister(jwt, request);
        return ApiEnvelope.success(new Registered(false));
    }
}
