package com.example.common.kafka.event;

import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.MessageDeletedPayload;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class ChatMessageDeletedEvent extends AbstractKafkaEvent {

    private final MessageDeletedPayload payload;

    @JsonCreator
    public ChatMessageDeletedEvent(
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("payload") MessageDeletedPayload payload
    ) {
        super(
                sourceService,
                ChatEventType.MESSAGE_DELETED.value(),
                createdAt,
                eventId
        );
        this.payload = payload;
    }

    public static ChatMessageDeletedEvent from(
            String sourceService,
            MessageDeletedPayload payload
    ) {
        return new ChatMessageDeletedEvent(
                sourceService,
                null,
                null,
                payload
        );
    }
}
