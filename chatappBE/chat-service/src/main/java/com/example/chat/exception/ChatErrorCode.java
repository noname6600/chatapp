package com.example.chat.exception;

import com.example.common.core.exception.IErrorCode;
import org.springframework.http.HttpStatus;

public enum ChatErrorCode implements IErrorCode {
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND),
    REPLY_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND),
    MESSAGE_CONTENT_EMPTY(HttpStatus.BAD_REQUEST),
    MESSAGE_DELETED(HttpStatus.BAD_REQUEST),
    INVALID_MESSAGE_TYPE(HttpStatus.BAD_REQUEST),
    REACTION_INVALID(HttpStatus.BAD_REQUEST),
    REACTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    REMOVED_FROM_GROUP(HttpStatus.FORBIDDEN),
    BLOCKED_SEND(HttpStatus.FORBIDDEN),

    ATTACHMENT_TOO_LARGE(HttpStatus.BAD_REQUEST),
    TOO_MANY_ATTACHMENTS(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_ATTACHMENT_TYPE(HttpStatus.BAD_REQUEST),
    ATTACHMENT_INVALID(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ChatErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}

