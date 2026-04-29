package com.example.common.integration.kafka.event;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class FriendRequestKafkaEvent extends AbstractKafkaEvent {

    private static final String EVENT_TYPE = "friend.request.event";

    private final FriendRequestEvent payload;

    @JsonCreator
    public FriendRequestKafkaEvent(
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("payload") FriendRequestEvent payload
    ) {
        super(sourceService, EVENT_TYPE, createdAt, eventId);
        this.payload = payload;
    }

    public static FriendRequestKafkaEvent of(
            String sourceService,
            FriendRequestEvent payload
    ) {
        return new FriendRequestKafkaEvent(sourceService, null, null, payload);
    }
}