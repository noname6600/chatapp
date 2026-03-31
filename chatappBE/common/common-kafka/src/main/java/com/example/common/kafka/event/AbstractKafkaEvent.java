package com.example.common.kafka.event;

import com.example.common.kafka.api.KafkaEvent;

import java.time.Instant;
import java.util.UUID;

public abstract class AbstractKafkaEvent implements KafkaEvent {

    private final UUID eventId;
    private final String eventType;
    private final String sourceService;
    private final Instant createdAt;

    protected AbstractKafkaEvent(
            String sourceService,
            String eventType,
            Instant createdAt,
            UUID eventId
    ) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID();
        this.eventType = eventType;
        this.sourceService = sourceService;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @Override public UUID getEventId() { return eventId; }
    @Override public String getEventType() { return eventType; }
    @Override public String getSourceService() { return sourceService; }
    @Override public Instant getCreatedAt() { return createdAt; }
}

