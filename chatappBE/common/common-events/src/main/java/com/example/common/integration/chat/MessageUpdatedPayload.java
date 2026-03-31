package com.example.common.integration.chat;

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
public final class MessageUpdatedPayload {

    private final UUID messageId;
    private final UUID roomId;

    private final Long seq;

    private final String content;

    private final Instant editedAt;

    @JsonCreator
    public MessageUpdatedPayload(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("seq") Long seq,
            @JsonProperty("content") String content,
            @JsonProperty("editedAt") Instant editedAt
    ) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.seq = seq;
        this.content = content;
        this.editedAt = editedAt;
    }
}