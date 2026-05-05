package com.example.common.kafka.flow;

import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;

/**
 * Routing context for Kafka events.
 * 
 * Captures metadata needed for routing and logging Kafka events.
 * Fields align with shared {@link com.example.common.event.EventMetadata}:
 * - topic: Kafka topic name (transport-specific routing destination)
 * - key: Kafka message key (optional, for partitioning)
 * - eventType: Semantic event type (e.g., "chat.message.sent")
 * - correlationId: Trace ID for event correlation
 * - sourceService: Originating service name
 * 
 * Alignment with shared EventMetadata ensures consistent field naming
 * across Kafka and Redis transports.
 * 
 * @param topic the Kafka topic name
 * @param key the Kafka message key (optional)
 * @param eventType the semantic event type
 * @param correlationId the correlation ID for event tracing
 * @param sourceService the originating service name
 * 
 * @since 2.0
 */
public record KafkaEventRoutingContext(
        String topic,
        String key,
        String eventType,
        String correlationId,
        String sourceService
) {
    /**
     * Creates a routing context from a Kafka topic, key, and event.
     * 
     * Extracts metadata from the event for use in routing and logging.
     * 
     * @param topic the Kafka topic name
     * @param key the Kafka message key
     * @param event the Kafka event (may be null)
     * @return new routing context
     */
    public static KafkaEventRoutingContext of(String topic, String key, EventEnvelope<?> event) {
        EventMetadata metadata = event == null ? null : event.metadata();
        return new KafkaEventRoutingContext(
                topic,
                key,
                metadata != null ? metadata.getEventType() : null,
                metadata != null ? metadata.getCorrelationId() : null,
                metadata != null ? metadata.getSourceService() : null
        );
    }
}
