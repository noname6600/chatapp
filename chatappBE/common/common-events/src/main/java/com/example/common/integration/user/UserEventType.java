package com.example.common.integration.user;

public enum UserEventType {

    PROFILE_CREATED("user.profile.created"),
    PROFILE_UPDATED("user.profile.updated");

    private final String value;

    UserEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
