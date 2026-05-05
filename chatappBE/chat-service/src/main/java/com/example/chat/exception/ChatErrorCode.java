package com.example.chat.exception;

import com.example.common.core.exception.IErrorCode;

public enum ChatErrorCode implements IErrorCode {
    MESSAGE_NOT_FOUND(404),
    REPLY_MESSAGE_NOT_FOUND(404),
    MESSAGE_CONTENT_EMPTY(400),
    MESSAGE_DELETED(400),
    INVALID_MESSAGE_TYPE(400),
    REACTION_INVALID(400),
    REACTION_NOT_ALLOWED(400),
    REMOVED_FROM_GROUP(403),
    BLOCKED_SEND(403),

    ATTACHMENT_TOO_LARGE(400),
    TOO_MANY_ATTACHMENTS(400),
    UNSUPPORTED_ATTACHMENT_TYPE(400),
    ATTACHMENT_INVALID(400);

    private final int status;

    ChatErrorCode(int status) {
        this.status = status;
    }

    @Override
    public int httpStatus() {
        return status;
    }
}

