package com.yongyong.backend.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final AccountService accounts;
    private final PhoneAuthService phoneAuth;

    public AccountController(AccountService accounts, PhoneAuthService phoneAuth) {
        this.accounts = accounts;
        this.phoneAuth = phoneAuth;
    }

    public record PhoneVerificationRequest(@NotBlank String phoneNumber) {}
    public record LoginRequest(@NotBlank String phoneNumber, @NotBlank String verificationCode,
                               @NotBlank String role) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record CreateProfileRequest(@NotBlank @Size(max = 100) String name,
                                       @Min(1900) @Max(2100) Integer birthYear,
                                       @Size(max = 500) String homeAddress,
                                       MobilityRequest mobility) {}
    public record UpdateProfileRequest(@Size(min = 1, max = 100) String name,
                                       @Min(1900) @Max(2100) Integer birthYear,
                                       @Size(max = 500) String homeAddress,
                                       MobilityRequest mobility) {}
    public record MobilityRequest(String mobilityAid, Boolean stairsDifficult,
                                  Boolean busDifficult, Boolean longWalkDifficult,
                                  Boolean walkingDiscomfort) {
        AccountService.MobilityUpdate toUpdate() {
            return new AccountService.MobilityUpdate(mobilityAid, stairsDifficult,
                    busDifficult, longWalkDifficult, walkingDiscomfort);
        }
    }
    public record HealthItem(@NotBlank @Size(max = 50) String conditionCode,
                             @Size(max = 500) String note) {}
    public record HealthRequest(@NotNull List<@Valid HealthItem> conditions) {}
    public record ConsentRequest(@NotBlank @Size(max = 50) String policyVersion, boolean granted) {}
    public record GuardianLinkRequest(@NotBlank String code, @Size(max = 50) String relationship) {}

    @PostMapping("/auth/phone-verifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPhoneVerification(@Valid @RequestBody PhoneVerificationRequest request) {
        phoneAuth.requestCode(request.phoneNumber());
    }

    @PostMapping("/auth/login")
    public PhoneAuthService.LoginResult login(@Valid @RequestBody LoginRequest request) {
        return phoneAuth.login(request.phoneNumber(), request.verificationCode(), request.role());
    }

    @PostMapping("/auth/refresh")
    public PhoneAuthService.LoginResult refresh(@Valid @RequestBody RefreshRequest request) {
        return phoneAuth.refresh(request.refreshToken());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        phoneAuth.logout(request.refreshToken());
    }

    @PostMapping("/elder-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.Profile createProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProfileRequest request) {
        return accounts.createProfile(jwt, request.name(), request.birthYear(), request.homeAddress(),
                request.mobility() == null ? null : request.mobility().toUpdate());
    }

    @GetMapping("/elder-profiles/me")
    public AccountService.Profile getOwnProfile(@AuthenticationPrincipal Jwt jwt) {
        return accounts.ownProfile(jwt);
    }

    @GetMapping("/elder-profiles/{elderId}")
    public AccountService.Profile getProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable String elderId) {
        return accounts.profile(jwt, elderId);
    }

    @PatchMapping("/elder-profiles/{elderId}")
    public AccountService.Profile updateProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable String elderId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return accounts.updateProfile(jwt, elderId, request.name(), request.birthYear(), request.homeAddress(),
                request.mobility() == null ? null : request.mobility().toUpdate());
    }

    @PutMapping("/elder-profiles/{elderId}/health-profile")
    public List<AccountService.HealthCondition> replaceHealth(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String elderId, @Valid @RequestBody HealthRequest request) {
        return accounts.replaceHealth(jwt, elderId, request.conditions().stream()
                .map(item -> new AccountService.HealthCondition(item.conditionCode(), item.note())).toList());
    }

    @PutMapping("/elder-profiles/{elderId}/consents/{scope}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordConsent(@AuthenticationPrincipal Jwt jwt, @PathVariable String elderId,
            @PathVariable String scope, @Valid @RequestBody ConsentRequest request) {
        accounts.recordConsent(jwt, elderId, scope, request.policyVersion(), request.granted());
    }

    @PostMapping("/elder-profiles/{elderId}/guardian-link-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.LinkCode issueLinkCode(@AuthenticationPrincipal Jwt jwt, @PathVariable String elderId) {
        return accounts.issueLinkCode(jwt, elderId);
    }

    @PostMapping("/guardian-links")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.GuardianLink connectGuardian(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GuardianLinkRequest request) {
        return accounts.connectGuardian(jwt, request.code(), request.relationship());
    }

    @DeleteMapping("/guardian-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnectGuardian(@AuthenticationPrincipal Jwt jwt, @PathVariable String linkId) {
        accounts.disconnectGuardian(jwt, linkId);
    }

    @GetMapping("/notifications")
    public List<AccountService.Notification> notifications(@AuthenticationPrincipal Jwt jwt) {
        return accounts.notifications(jwt);
    }

    @PatchMapping("/notifications/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markNotificationRead(@AuthenticationPrincipal Jwt jwt, @PathVariable String notificationId) {
        accounts.markNotificationRead(jwt, notificationId);
    }
}
