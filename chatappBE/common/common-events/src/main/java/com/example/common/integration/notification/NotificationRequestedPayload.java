package com.example.common.integration.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NotificationRequestedPayload {

    private final UUID notificationId;
    private final UUID userId;
    private final String title;
    private final String content;
    private final String type;

    @JsonCreator
    public NotificationRequestedPayload(
            @JsonProperty("notificationId") UUID notificationId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("type") String type
    ) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.type = type;
    }
}

