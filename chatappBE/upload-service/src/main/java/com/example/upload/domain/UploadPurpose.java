package com.example.upload.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

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

    @JsonCreator
    public static UploadPurpose fromValue(String raw) {
        if (raw == null) {
            return null;
        }

        for (UploadPurpose purpose : values()) {
            if (purpose.value.equalsIgnoreCase(raw)) {
                return purpose;
            }
        }

        return null;
    }
}
