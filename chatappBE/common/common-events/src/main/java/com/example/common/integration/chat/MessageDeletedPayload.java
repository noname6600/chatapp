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
public final class MessageDeletedPayload {

    private final UUID messageId;
    private final UUID roomId;

    private final Long seq;

    private final Instant deletedAt;
    private final UUID deletedBy;

    @JsonCreator
    public MessageDeletedPayload(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("seq") Long seq,
            @JsonProperty("deletedAt") Instant deletedAt,
            @JsonProperty("deletedBy") UUID deletedBy
    ) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.seq = seq;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
    }
}
