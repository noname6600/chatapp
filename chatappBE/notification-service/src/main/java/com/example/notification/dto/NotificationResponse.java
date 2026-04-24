package com.example.notification.dto;

import com.example.notification.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String type;
    private UUID referenceId;
    private UUID roomId;
    private UUID senderId;
    private UUID actorId;
    private String actorDisplayName;
    private String senderName;
    private String preview;
    private boolean isRead;
    private boolean actionRequired;
    private String createdAt;

    public static NotificationResponse from(Notification n) {
        UUID actorId = n.getActorId();
        String actorDisplayName = n.getActorDisplayName();
        String senderName = n.getSenderName();
        String normalizedSender = actorDisplayName != null && !actorDisplayName.isBlank()
                ? actorDisplayName
                : senderName;

        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType() == null ? null : n.getType().name())
                .referenceId(n.getReferenceId())
                .roomId(n.getRoomId())
                .senderId(actorId)
                .actorId(actorId)
                .actorDisplayName(actorDisplayName)
                .senderName(normalizedSender)
                .preview(n.getPreview())
                .isRead(n.isRead())
                .actionRequired(n.isActionRequired())
                .createdAt(n.getCreatedAt() == null ? null : n.getCreatedAt().toString())
                .build();
    }
}
