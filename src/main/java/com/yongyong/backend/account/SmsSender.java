package com.yongyong.backend.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SmsSender {
    public boolean available() { return false; }

    public void send(String phoneNumber, String message) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider is not configured");
    }
}
