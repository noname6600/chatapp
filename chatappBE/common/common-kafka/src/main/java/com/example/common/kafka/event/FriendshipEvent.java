package com.example.common.kafka.event;

import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendshipPayload;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class FriendshipEvent extends AbstractKafkaEvent {

    private final FriendshipPayload payload;

    @JsonCreator
    public FriendshipEvent(
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("payload") FriendshipPayload payload
    ) {
        super(sourceService, eventType, createdAt, eventId);
        this.payload = payload;
    }

    public static FriendshipEvent of(String sourceService,
                                     FriendshipEventType type,
                                     FriendshipPayload payload) {
        return new FriendshipEvent(sourceService, null, null, type.value(), payload);
    }
}



