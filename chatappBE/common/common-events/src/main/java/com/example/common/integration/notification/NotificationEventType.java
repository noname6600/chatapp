package com.example.common.integration.notification;

public enum NotificationEventType {

    REQUESTED("notification.requested");

    private final String value;

    NotificationEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
