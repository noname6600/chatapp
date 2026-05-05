package com.example.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.Instant;

/**
 * Immutable metadata for all events.
 * 
 * Contains identifying and routing information shared across all transport mechanisms
 * (Kafka, Redis, etc.). This is the single source of truth for event metadata field names
 * and semantics.
 * 
 * Fields:
 * - eventId: Unique identifier for this event instance
 * - eventType: Semantic type of the event (e.g., "chat.message.sent"), follows pattern: lowercase.dot.separated.hyphenated-names
 * - sourceService: Name of the service that originated this event (e.g., "chat-service", "auth-service")
 * - createdAt: Instant when the event was created
 * - correlationId: Correlation ID for tracing related events across the system
 * 
 * @since 2.0
 */
@Getter
public final class EventMetadata {
    private final String eventId;
    private final String eventType;
    private final String sourceService;
    private final Instant createdAt;
    private final String correlationId;
    
    /**
     * Creates event metadata with all required fields.
     * 
     * @param eventId unique event identifier
     * @param eventType semantic event type (lowercase.dot.separated.hyphenated-names)
     * @param sourceService originating service name
     * @param createdAt creation timestamp
     * @param correlationId correlation ID for tracing
     */
    @JsonCreator
    public EventMetadata(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("sourceService") String sourceService,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("correlationId") String correlationId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be null or blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        if (sourceService == null || sourceService.isBlank()) {
            throw new IllegalArgumentException("sourceService must not be null or blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be null or blank");
        }
        this.eventId = eventId;
        this.eventType = eventType;
        this.sourceService = sourceService;
        this.createdAt = createdAt;
        this.correlationId = correlationId;
    }
    
    /**
     * Creates event metadata with minimal fields (for builder patterns).
     * 
     * Useful for constructing metadata when some fields have defaults.
     * 
     * @param eventId unique event identifier
     * @param eventType semantic event type
     * @param sourceService originating service name
     * @param createdAt creation timestamp
     * @return new EventMetadata with correlationId set to eventId
     */
    public static EventMetadata of(String eventId, String eventType, String sourceService, Instant createdAt) {
        return new EventMetadata(eventId, eventType, sourceService, createdAt, eventId);
    }
    
    /**
     * Returns a string representation of this metadata.
     */
    @Override
    public String toString() {
        return "EventMetadata{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", sourceService='" + sourceService + '\'' +
                ", createdAt=" + createdAt +
                ", correlationId='" + correlationId + '\'' +
                '}';
    }
}
