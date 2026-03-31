package com.example.common.kafka.api;

import java.time.Instant;
import java.util.UUID;

public interface KafkaEvent<T> {
    UUID getEventId();
    String getEventType();
    String getSourceService();
    Instant getCreatedAt();
    T getPayload();
}

