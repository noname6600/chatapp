package com.example.common.integration.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code chat.message.pinned} and {@code chat.message.unpinned} events.
 *
 * <p>Owned by {@code common-events} as a shared contract so all consumers can
 * deserialize these events without requiring a service-local payload class.
 */
@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MessagePinPayload {

    private final UUID messageId;
    private final UUID roomId;
    private final UUID actorUserId;
    private final Instant pinnedAt;

    @JsonCreator
    public MessagePinPayload(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("actorUserId") UUID actorUserId,
            @JsonProperty("pinnedAt") Instant pinnedAt
    ) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.actorUserId = actorUserId;
        this.pinnedAt = pinnedAt;
    }
}
