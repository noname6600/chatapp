package com.example.common.core.exception;

public class BusinessException extends RuntimeException {

    private final IErrorCode errorCode;
    private final Object details;

    public BusinessException(IErrorCode errorCode) {
        this(errorCode, errorCode.name(), null);
    }

    public BusinessException(IErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(IErrorCode errorCode, Object details) {
        this(errorCode, errorCode.name(), details);
    }

    public BusinessException(IErrorCode errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public IErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
