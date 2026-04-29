package com.example.common.integration.kafka.event;

import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.MessageUpdatedPayload;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class ChatMessageEditedEvent extends AbstractKafkaEvent {

    private final MessageUpdatedPayload payload;

    @JsonCreator
    public ChatMessageEditedEvent(
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("payload") MessageUpdatedPayload payload
    ) {
        super(
                sourceService,
                ChatEventType.MESSAGE_EDITED.value(),
                createdAt,
                eventId
        );
        this.payload = payload;
    }

    public static ChatMessageEditedEvent from(
            String sourceService,
            MessageUpdatedPayload payload
    ) {
        return new ChatMessageEditedEvent(
                sourceService,
                null,
                null,
                payload
        );
    }
}