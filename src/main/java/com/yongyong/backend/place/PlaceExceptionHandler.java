package com.yongyong.backend.place;

import com.yongyong.backend.api.ApiEnvelope;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PlaceController.class)
public class PlaceExceptionHandler {
    @ExceptionHandler(PlaceApiException.class)
    public ResponseEntity<ApiEnvelope<Void>> handlePlaceApiException(PlaceApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiEnvelope.failure(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiEnvelope<Void>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiEnvelope.failure("VALIDATION_ERROR", "요청 값을 확인해 주세요."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccountAccessException(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String code = status == 401 ? "UNAUTHENTICATED"
                : status == 403 ? "FORBIDDEN" : "REQUEST_REJECTED";
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiEnvelope.failure(code, "요청을 처리할 수 없습니다."));
    }
}
