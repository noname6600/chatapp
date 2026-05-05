package com.example.upload.domain;

public enum UploadPurpose {
    CHAT_ATTACHMENT("chat-attachment"),
    USER_AVATAR("user-avatar");

    private final String value;

    UploadPurpose(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
