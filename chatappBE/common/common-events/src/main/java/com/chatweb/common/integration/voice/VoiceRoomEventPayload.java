package com.chatweb.common.integration.voice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class VoiceRoomEventPayload {

    private final String eventType;
    private final UUID chatRoomId;
    private final String lkRoomName;
    private final UUID userId;
    private final String username;
    private final String avatarUrl;
    private final int participantCount;
    private final long timestamp;

    @JsonCreator
    public VoiceRoomEventPayload(
            @JsonProperty("eventType") String eventType,
            @JsonProperty("chatRoomId") UUID chatRoomId,
            @JsonProperty("lkRoomName") String lkRoomName,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("username") String username,
            @JsonProperty("avatarUrl") String avatarUrl,
            @JsonProperty("participantCount") int participantCount,
            @JsonProperty("timestamp") long timestamp
    ) {
        this.eventType = eventType;
        this.chatRoomId = chatRoomId;
        this.lkRoomName = lkRoomName;
        this.userId = userId;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.participantCount = participantCount;
        this.timestamp = timestamp;
    }
}
