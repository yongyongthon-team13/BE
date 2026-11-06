package com.yongyong.backend.route;

import com.yongyong.backend.api.ApiEnvelope;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackageClasses = RoutePlanController.class)
public class RouteExceptionHandler {
    @ExceptionHandler(RouteApiException.class)
    public ResponseEntity<ApiEnvelope<Void>> routeApi(RouteApiException e) {
        return ResponseEntity.status(e.status()).body(ApiEnvelope.failure(e.code(), e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiEnvelope<Void>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(ApiEnvelope.failure("VALIDATION_ERROR", "요청 값을 확인해 주세요."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiEnvelope<Void>> access(ResponseStatusException e) {
        String code = e.getStatusCode().value() == 404 ? "NOT_FOUND" : "FORBIDDEN";
        return ResponseEntity.status(e.getStatusCode()).body(ApiEnvelope.failure(code, "경로 정보를 조회할 수 없습니다."));
    }
}
