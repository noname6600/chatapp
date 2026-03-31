package com.example.notification.dto;

import com.example.notification.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String type;
    private UUID referenceId;
    private UUID roomId;
    private String senderName;
    private String preview;
    private boolean isRead;
    private Instant createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType() == null ? null : n.getType().name())
                .referenceId(n.getReferenceId())
                .roomId(n.getRoomId())
                .senderName(n.getSenderName())
                .preview(n.getPreview())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
