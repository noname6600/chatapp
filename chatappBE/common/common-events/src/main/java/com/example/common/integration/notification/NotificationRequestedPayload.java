package com.example.common.integration.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class NotificationRequestedPayload {
    private UUID notificationId;
    private UUID userId;
    private String title;
    private String content;
    private String type;
}

