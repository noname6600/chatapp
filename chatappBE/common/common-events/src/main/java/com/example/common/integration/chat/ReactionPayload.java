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

    private final UUID messageAuthorId;

    private final String actorDisplayName;

    @JsonCreator
    public ReactionPayload(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("userId") UUID userId,
            @JsonProperty("emoji") String emoji,
            @JsonProperty("action") ReactionAction action,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("messageAuthorId") UUID messageAuthorId,
            @JsonProperty("actorDisplayName") String actorDisplayName
    ) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.userId = userId;
        this.emoji = emoji;
        this.action = action;
        this.createdAt = createdAt;
        this.messageAuthorId = messageAuthorId;
        this.actorDisplayName = actorDisplayName;
    }
}