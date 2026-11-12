package com.yongyong.backend.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class PushTokenDtos {
    private PushTokenDtos() {}
    public enum Platform { IOS, ANDROID }
    public record Register(@NotBlank @Pattern(regexp = "^(Expo|Exponent)PushToken\\[[^]]+\\]$") String token,
                           @NotNull Platform platform) {}
    public record Unregister(@NotBlank @Pattern(regexp = "^(Expo|Exponent)PushToken\\[[^]]+\\]$") String token) {}
    public record Registered(boolean registered) {}
}
