package com.example.common.redis.serialization;

import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;
import com.example.common.event.EventPayloadRegistry;
import com.example.common.event.SharedEventCatalog;
import com.example.common.redis.exception.RedisPubSubException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Canonical Redis event serializer.
 *
 * <p>Deserialize errors are classified into distinct failure categories so callers can
 * distinguish invalid JSON, missing metadata, unknown event type, invalid payload shape,
 * and invalid metadata identity fields. Each category produces a distinct exception message.
 *
 * <p>Payload-less event types (listed in {@link SharedEventCatalog#PAYLOAD_LESS_EVENT_TYPES})
 * are deserialized with a {@code null} payload instead of triggering a registry lookup.
 */
@RequiredArgsConstructor
public class JsonRedisEventSerializer implements RedisEventSerializer {

    private final ObjectMapper mapper;
    private final EventPayloadRegistry registry;

    @Override
    public String serialize(EventEnvelope<?> envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RedisPubSubException(
                    "unknown",
                    "Redis serialize failed for EventEnvelope",
                    e
            );
        }
    }

    @Override
    public EventEnvelope<?> deserialize(String payload) {
        // Phase 1: parse raw JSON
        JsonNode node;
        try {
            node = mapper.readTree(payload);
        } catch (Exception e) {
            throw new RedisPubSubException("unknown", "Redis deserialize failed: invalid JSON", e);
        }

        // Phase 2: extract and validate metadata node
        JsonNode metadataNode = node.get("metadata");
        if (metadataNode == null || metadataNode.isNull()) {
            throw new RedisPubSubException("unknown",
                    "Redis deserialize failed: missing metadata field", null);
        }

        String eventType = textOrNull(metadataNode, "eventType");
        if (eventType == null || eventType.isBlank()) {
            throw new RedisPubSubException("unknown",
                    "Redis deserialize failed: missing eventType in metadata", null);
        }

        // Phase 3: resolve payload — skip for known payload-less event types
        Object payloadObject = null;
        JsonNode payloadNode = node.get("payload");

        if (!SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES.contains(eventType)) {
            Class<?> payloadClass;
            try {
                payloadClass = registry.resolvePayload(eventType);
            } catch (IllegalStateException e) {
                throw new RedisPubSubException("unknown",
                        "Redis deserialize failed: unknown event type: " + eventType, e);
            }

            // Payload-bearing events must have a non-null payload
            if (payloadNode == null || payloadNode.isNull()) {
                throw new RedisPubSubException("unknown",
                        "Redis deserialize failed: missing payload for payload-bearing event type: " + eventType, null);
            }

            try {
                payloadObject = mapper.treeToValue(payloadNode, payloadClass);
            } catch (Exception e) {
                throw new RedisPubSubException("unknown",
                        "Redis deserialize failed: invalid payload for event type: " + eventType, e);
            }
        }

        // Phase 4: build metadata — constructor validates all identity fields
        Instant createdAt;
        try {
            createdAt = instantOrNull(metadataNode, "createdAt");
        } catch (DateTimeParseException e) {
            throw new RedisPubSubException("unknown",
                    "Redis deserialize failed: invalid createdAt timestamp format", e);
        }

        EventMetadata metadata;
        try {
            metadata = new EventMetadata(
                    textOrNull(metadataNode, "eventId"),
                    eventType,
                    textOrNull(metadataNode, "sourceService"),
                    createdAt,
                    textOrNull(metadataNode, "correlationId")
            );
        } catch (IllegalArgumentException e) {
            throw new RedisPubSubException("unknown",
                    "Redis deserialize failed: invalid envelope metadata: " + e.getMessage(), e);
        }

        return new EventEnvelope<>(metadata, payloadObject);
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) return null;
        String value = field.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Instant instantOrNull(JsonNode node, String fieldName) {
        String value = textOrNull(node, fieldName);
        return value == null ? null : Instant.parse(value);
    }
}
