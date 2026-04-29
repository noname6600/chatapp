package com.example.common.web.exception;

import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.common.web.response.ApiError;
import com.example.common.web.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("Business error [{}]: {}", ex.getErrorCode(), ex.getMessage());

        ApiError error = new ApiError(
                ex.getErrorCode().name(),
                ex.getMessage(),
                ex.getDetails()
        );

        return ResponseEntity
                .status(ex.getErrorCode().getStatus())
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));

        String message = errors.values().stream()
                .findFirst()
                .orElse("Validation failed");

        ApiError error = new ApiError(
                CommonErrorCode.VALIDATION_ERROR.name(),
                message,
                errors
        );

        return ResponseEntity
                .status(CommonErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);

        ApiError error = new ApiError(
                CommonErrorCode.INTERNAL_ERROR.name(),
                "Internal server error",
                null
        );

        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            errors.put(field, violation.getMessage());
        });

        ApiError error = new ApiError(
                CommonErrorCode.VALIDATION_ERROR.name(),
                "Validation failed",
                errors
        );

        return ResponseEntity
                .status(CommonErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException ex) {
        ApiError error = new ApiError(
                CommonErrorCode.BAD_REQUEST.name(),
                "Malformed JSON request",
                null
        );

        return ResponseEntity
                .status(CommonErrorCode.BAD_REQUEST.getStatus())
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ApiError error = new ApiError(
                CommonErrorCode.FORBIDDEN.name(),
                "Access denied",
                null
        );

        return ResponseEntity
                .status(CommonErrorCode.FORBIDDEN.getStatus())
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthenticationException ex) {
        ApiError error = new ApiError(
                CommonErrorCode.UNAUTHORIZED.name(),
                "Unauthorized",
                null
        );

        return ResponseEntity
                .status(CommonErrorCode.UNAUTHORIZED.getStatus())
                .body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ApiError error = new ApiError(
                CommonErrorCode.BAD_REQUEST.name(),
                "Invalid parameter type",
                ex.getName()
        );

        return ResponseEntity
                .status(CommonErrorCode.BAD_REQUEST.getStatus())
                .body(ApiResponse.failure(error));
    }
}
