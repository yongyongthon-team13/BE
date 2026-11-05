package com.yongyong.backend.trip;

import com.yongyong.backend.api.ApiEnvelope;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = TripController.class)
public class TripExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiEnvelope<Void>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(ApiEnvelope.failure("VALIDATION_ERROR", "요청 값을 확인해 주세요."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiEnvelope<Void>> request(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String code = status == 404 ? "NOT_FOUND" : status == 409 ? "TRIP_NOT_ACTIVE"
                : status == 422 ? "CONSENT_REQUIRED" : status == 403 ? "FORBIDDEN" : "VALIDATION_ERROR";
        return ResponseEntity.status(e.getStatusCode()).body(ApiEnvelope.failure(code, "이동 요청을 처리할 수 없습니다."));
    }
}
