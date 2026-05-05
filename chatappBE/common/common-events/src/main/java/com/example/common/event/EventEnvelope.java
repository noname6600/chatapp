package com.example.common.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic event envelope that wraps metadata and payload.
 * 
 * This is the standard container for all events across the system. It combines
 * {@link EventMetadata} (identifying and routing information) with a generic payload
 * (domain-specific data).
 * 
 * Services should use EventEnvelope to publish and consume events, rather than
 * creating transport-specific event types.
 * 
 * Example usage:
 * <pre>
 *   EventMetadata metadata = new EventMetadata(
 *       "evt-123", 
 *       "chat.message.sent",
 *       "chat-service",
 *       Instant.now(),
 *       "corr-456"
 *   );
 *   ChatMessagePayload payload = new ChatMessagePayload(...);
 *   EventEnvelope<ChatMessagePayload> envelope = new EventEnvelope<>(metadata, payload);
 * </pre>
 * 
 * @param metadata event metadata (routing and identity information)
 * @param payload domain-specific event payload
 * @param <T> the type of the payload
 * 
 * @since 2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
        @JsonProperty("metadata") EventMetadata metadata,
        @JsonProperty("payload") T payload
) implements Event<T> {
    
    /**
     * Creates an EventEnvelope with metadata and payload.
     * 
     * @param metadata the event metadata, must not be null
     * @param payload the domain-specific payload, may be null
     */
    @JsonCreator
    public EventEnvelope {
        if (metadata == null) {
            throw new IllegalArgumentException("EventEnvelope metadata must not be null");
        }
    }
    
    /**
     * Returns the metadata associated with this event.
     * 
     * @return the event metadata
     */
    @Override
    public EventMetadata metadata() {
        return metadata;
    }
    
    /**
     * Returns the payload contained in this event.
     * 
     * @return the event payload
     */
    @Override
    public T payload() {
        return payload;
    }
}
