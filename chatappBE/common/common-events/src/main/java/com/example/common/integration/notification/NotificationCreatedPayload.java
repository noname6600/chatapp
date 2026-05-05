package com.example.common.integration.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NotificationCreatedPayload {

    private final UUID notificationId;
    private final UUID userId;
    private final NotificationType type;
    private final UUID referenceId;
    private final UUID roomId;
    private final String senderName;
    private final String preview;
    private final Instant createdAt;

    @JsonCreator
    public NotificationCreatedPayload(
            @JsonProperty("notificationId") UUID notificationId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("type") NotificationType type,
            @JsonProperty("referenceId") UUID referenceId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("senderName") String senderName,
            @JsonProperty("preview") String preview,
            @JsonProperty("createdAt") Instant createdAt
    ) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.type = type;
        this.referenceId = referenceId;
        this.roomId = roomId;
        this.senderName = senderName;
        this.preview = preview;
        this.createdAt = createdAt;
    }

    public enum NotificationType {
        MESSAGE,
        MENTION,
        FRIEND_REQUEST,
        FRIEND_REQUEST_ACCEPTED
    }
}
