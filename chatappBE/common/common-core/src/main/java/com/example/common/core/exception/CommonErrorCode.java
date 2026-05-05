package com.example.common.core.exception;

public enum CommonErrorCode implements IErrorCode {
    BAD_REQUEST(400),
    VALIDATION_ERROR(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    PERMISSION_DENIED(403),
    RESOURCE_NOT_FOUND(404),
    CONFLICT(409),
    INTERNAL_ERROR(500);

    private final int status;

    CommonErrorCode(int status) {
        this.status = status;
    }

    @Override
    public int httpStatus() {
        return status;
    }
}
