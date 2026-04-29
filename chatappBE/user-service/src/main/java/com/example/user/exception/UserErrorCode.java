package com.example.user.exception;

import com.example.common.core.exception.IErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements IErrorCode {
    ATTACHMENT_TOO_LARGE(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    UserErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}

