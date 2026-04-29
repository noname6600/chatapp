package com.example.auth.exception;

import com.example.common.core.exception.IErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements IErrorCode {
    INCOMPLETE_ACCOUNT(HttpStatus.CONFLICT),

    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),

    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED);

    private final HttpStatus status;

    AuthErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}

