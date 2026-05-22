package com.chatweb.auth.exception;

import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.web.response.ApiError;
import com.chatweb.common.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class AuthExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("auth data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        ApiError error = new ApiError(
                CommonErrorCode.CONFLICT.name(),
                "Email already registered",
                null
        );

        return ResponseEntity
                .status(CommonErrorCode.CONFLICT.httpStatus())
                .body(ApiResponse.failure(error));
    }
}
