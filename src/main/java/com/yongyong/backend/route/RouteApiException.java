package com.yongyong.backend.route;

import org.springframework.http.HttpStatus;

public class RouteApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public RouteApiException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
