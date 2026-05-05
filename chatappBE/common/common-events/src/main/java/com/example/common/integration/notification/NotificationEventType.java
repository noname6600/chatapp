package com.example.common.integration.notification;

public enum NotificationEventType {

    NOTIFICATION_REQUESTED("notification.requested"),
    NOTIFICATION_CREATED("notification.created"),

    /**
     * Emitted when a notification has been successfully delivered to the target user.
     * This event type is payload-less in the shared contract.
     */
    NOTIFICATION_SENT("notification.sent");

    private final String value;

    NotificationEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
