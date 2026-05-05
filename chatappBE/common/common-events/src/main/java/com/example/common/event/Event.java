package com.example.common.event;

/**
 * Base interface for all events in the system.
 * 
 * Provides a unified contract for events regardless of transport (Kafka, Redis, etc).
 * All events contain metadata (identifying and routing information) and a payload (domain data).
 * 
 * @param <T> The payload type contained in this event
 * 
 * @since 2.0
 */
public interface Event<T> {
    
    /**
     * Returns the metadata associated with this event.
     * 
     * Metadata includes event ID, type, source service, creation timestamp, and correlation ID
     * for tracing and routing purposes.
     * 
     * @return the event metadata, never null
     */
    EventMetadata metadata();
    
    /**
     * Returns the domain-specific payload contained in this event.
     * 
     * The payload is the actual business data being communicated (e.g., ChatMessagePayload,
     * AccountCreatedPayload, etc.).
     * 
     * @return the event payload, may be null
     */
    T payload();
}
