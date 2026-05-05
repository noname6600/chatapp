package com.example.auth.exception;

import com.example.common.core.exception.IErrorCode;

public enum AuthErrorCode implements IErrorCode {
    INCOMPLETE_ACCOUNT(409),

    TOKEN_EXPIRED(401),
    TOKEN_INVALID(401),

    REFRESH_TOKEN_EXPIRED(401),
    REFRESH_TOKEN_INVALID(401),
    REFRESH_TOKEN_REVOKED(401);

    private final int status;

    AuthErrorCode(int status) {
        this.status = status;
    }

    @Override
    public int httpStatus() {
        return status;
    }
}

