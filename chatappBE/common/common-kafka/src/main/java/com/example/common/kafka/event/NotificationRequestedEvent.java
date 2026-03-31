package com.example.common.kafka.event;

import com.example.common.integration.notification.NotificationEventType;
import com.example.common.integration.notification.NotificationRequestedPayload;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class NotificationRequestedEvent extends AbstractKafkaEvent {

    private final NotificationRequestedPayload payload;

    @JsonCreator
    public NotificationRequestedEvent(
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("payload") NotificationRequestedPayload payload
    ) {
        super(
                sourceService,
                NotificationEventType.REQUESTED.value(),
                createdAt,
                eventId
        );
        this.payload = payload;
    }

    public static NotificationRequestedEvent from(String sourceService,
                                                  NotificationRequestedPayload payload) {
        return new NotificationRequestedEvent(sourceService, null, null, payload);
    }
}


