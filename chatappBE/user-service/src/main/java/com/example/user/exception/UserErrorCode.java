package com.example.user.exception;

import com.example.common.core.exception.IErrorCode;

public enum UserErrorCode implements IErrorCode {
    ATTACHMENT_TOO_LARGE(400);

    private final int status;

    UserErrorCode(int status) {
        this.status = status;
    }

    @Override
    public int httpStatus() {
        return status;
    }
}

