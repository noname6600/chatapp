package com.example.common.web.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),

    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED),

    FORBIDDEN(HttpStatus.FORBIDDEN),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND),
    REPLY_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND),

    CONFLICT(HttpStatus.CONFLICT),

    MESSAGE_CONTENT_EMPTY(HttpStatus.BAD_REQUEST),
    MESSAGE_DELETED(HttpStatus.BAD_REQUEST),

    ATTACHMENT_TOO_LARGE(HttpStatus.BAD_REQUEST),
    TOO_MANY_ATTACHMENTS(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_ATTACHMENT_TYPE(HttpStatus.BAD_REQUEST),
    ATTACHMENT_INVALID(HttpStatus.BAD_REQUEST),

    INVALID_MESSAGE_TYPE(HttpStatus.BAD_REQUEST),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    REACTION_INVALID(HttpStatus.BAD_REQUEST),
    REACTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}