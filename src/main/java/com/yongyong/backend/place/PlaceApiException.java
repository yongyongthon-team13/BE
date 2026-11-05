package com.yongyong.backend.place;

import org.springframework.http.HttpStatus;

public class PlaceApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public PlaceApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
