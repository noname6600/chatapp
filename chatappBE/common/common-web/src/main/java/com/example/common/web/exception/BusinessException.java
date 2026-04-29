package com.example.common.web.exception;

@Deprecated(forRemoval = true)
public class BusinessException extends com.example.common.core.exception.BusinessException {

    public BusinessException(IErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(IErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(IErrorCode errorCode, Object details) {
        super(errorCode, details);
    }

    public BusinessException(IErrorCode errorCode, String message, Object details) {
        super(errorCode, message, details);
    }
}
