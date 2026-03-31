package com.example.common.integration.friendship;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FriendRequestEvent {

    private final UUID senderId;
    private final UUID recipientId;
    private final UUID requestId;
    private final Type type;

    @JsonCreator
    public FriendRequestEvent(
            @JsonProperty("senderId") UUID senderId,
            @JsonProperty("recipientId") UUID recipientId,
            @JsonProperty("requestId") UUID requestId,
            @JsonProperty("type") Type type
    ) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.requestId = requestId;
        this.type = type;
    }

    public enum Type {
        SENT,
        ACCEPTED
    }
}