package com.example.common.integration.chat;

import com.example.common.integration.enums.ReactionAction;
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
public final class ReactionPayload {

    private final UUID messageId;

    private final UUID roomId;

    private final UUID userId;

    private final String emoji;

    private final ReactionAction action;

    private final Instant createdAt;

    @JsonCreator
    public ReactionPayload(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("emoji") String emoji,
            @JsonProperty("action") ReactionAction action,
            @JsonProperty("createdAt") Instant createdAt
    ) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.userId = userId;
        this.emoji = emoji;
        this.action = action;
        this.createdAt = createdAt;
    }
}