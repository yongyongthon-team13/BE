package com.yongyong.backend.api;

public record ApiEnvelope<T>(T data, ApiError error, ApiMeta meta) {
    public static <T> ApiEnvelope<T> success(T data) {
        return new ApiEnvelope<>(data, null, new ApiMeta("req_" + java.util.UUID.randomUUID()));
    }

    public static <T> ApiEnvelope<T> failure(String code, String message) {
        return new ApiEnvelope<>(null, new ApiError(code, message),
                new ApiMeta("req_" + java.util.UUID.randomUUID()));
    }

    public record ApiError(String code, String message) {}
    public record ApiMeta(String requestId) {}
}
