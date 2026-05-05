package com.example.common.integration.friendship;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload DTO for friend request events.
 *
 * <p>Used as the payload inside {@link com.example.common.event.EventEnvelope} for
 * {@link FriendshipEventType#FRIEND_REQUEST_SENT}, {@link FriendshipEventType#FRIEND_REQUEST_ACCEPTED},
 * {@link FriendshipEventType#FRIEND_REQUEST_DECLINED}, and {@link FriendshipEventType#FRIEND_REQUEST_CANCELLED}.
 *
 * <p>The event type is carried in {@link com.example.common.event.EventMetadata#getEventType()}
 * on the envelope. Use {@link FriendshipEventType} to determine the action — do not add a nested
 * action enum here.
 */
@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FriendRequestPayload {

    private final UUID senderId;
    private final UUID recipientId;
    private final UUID requestId;
    private final String senderDisplayName;
    private final Instant createdAt;

    @JsonCreator
    public FriendRequestPayload(
            @JsonProperty("senderId") UUID senderId,
            @JsonProperty("recipientId") UUID recipientId,
            @JsonProperty("requestId") UUID requestId,
            @JsonProperty("senderDisplayName") String senderDisplayName,
            @JsonProperty("createdAt") Instant createdAt
    ) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.requestId = requestId;
        this.senderDisplayName = senderDisplayName;
        this.createdAt = createdAt;
    }
}
