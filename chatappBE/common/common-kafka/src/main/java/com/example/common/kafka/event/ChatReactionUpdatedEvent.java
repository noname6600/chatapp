package com.example.common.kafka.event;

import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.ReactionPayload;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class ChatReactionUpdatedEvent
        extends AbstractKafkaEvent {

    private final ReactionPayload payload;

    @JsonCreator
    public ChatReactionUpdatedEvent(

            @JsonProperty("sourceService")
            String sourceService,

            @JsonProperty("eventId")
            UUID eventId,

            @JsonProperty("createdAt")
            Instant createdAt,

            @JsonProperty("payload")
            ReactionPayload payload
    ) {

        super(
                sourceService,
                ChatEventType.REACTION_UPDATED.value(),
                createdAt,
                eventId
        );

        this.payload = payload;
    }

    public static ChatReactionUpdatedEvent from(
            String sourceService,
            ReactionPayload payload
    ) {

        return new ChatReactionUpdatedEvent(
                sourceService,
                null,
                null,
                payload
        );
    }
}
