package com.example.common.kafka.event;

import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.ChatMessagePayload;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class ChatMessageSentEvent extends AbstractKafkaEvent {

    private final ChatMessagePayload payload;

    @JsonCreator
    public ChatMessageSentEvent(
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("payload") ChatMessagePayload payload
    ) {
        super(
                sourceService,
                ChatEventType.MESSAGE_SENT.value(),
                createdAt,
                eventId
        );
        this.payload = payload;
    }

    public static ChatMessageSentEvent from(
            String sourceService,
            ChatMessagePayload payload
    ) {
        return new ChatMessageSentEvent(
                sourceService,
                null,
                null,
                payload
        );
    }
}

