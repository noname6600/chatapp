package com.chatweb.common.kafka.serialization;

import com.chatweb.common.event.DefaultEventPayloadRegistry;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.EventPayloadRegistry;
import com.chatweb.common.event.SharedEventCatalog;
import com.chatweb.common.event.validation.EventContractValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * JSON deserializer for EventEnvelope values using the shared payload registry.
 */
public class EventEnvelopeKafkaDeserializer implements Deserializer<EventEnvelope<?>> {

    private final ObjectMapper objectMapper;
    private final EventPayloadRegistry registry;

    public EventEnvelopeKafkaDeserializer() {
        this(createDefaultMapper(), createDefaultRegistry());
    }

    public EventEnvelopeKafkaDeserializer(ObjectMapper objectMapper, EventPayloadRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    public EventEnvelope<?> deserialize(String topic, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Kafka deserializer requires non-null payload bytes");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("Kafka deserializer requires non-empty payload bytes");
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode metadataNode = root.get("metadata");
            if (metadataNode == null || metadataNode.isNull()) {
                throw new IllegalArgumentException("Missing metadata in Kafka event envelope");
            }

            String eventType = textOrNull(metadataNode, "eventType");
            EventContractValidator.validateEventNameOrThrow(eventType);
            boolean isPayloadLess = SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES.contains(eventType);
            boolean isPayloadBearing = registry.contains(eventType);
            if (!isPayloadLess && !isPayloadBearing) {
                throw new IllegalArgumentException("Unknown event type: " + eventType);
            }

            JsonNode payloadNode = root.get("payload");
            Object payload = null;
            if (SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES.contains(eventType)) {
                if (payloadNode != null && !payloadNode.isNull()) {
                    throw new IllegalArgumentException("Unexpected payload for payload-less event type: " + eventType);
                }
            } else {
                Class<?> payloadClass;
                try {
                    payloadClass = registry.resolvePayload(eventType);
                } catch (IllegalStateException ex) {
                    throw new IllegalArgumentException("Unknown payload-bearing event type: " + eventType, ex);
                }
                if (payloadNode == null || payloadNode.isNull()) {
                    throw new IllegalArgumentException("Missing payload for payload-bearing event type: " + eventType);
                }
                payload = objectMapper.treeToValue(payloadNode, payloadClass);
            }

            EventMetadata metadata = objectMapper.treeToValue(metadataNode, EventMetadata.class);

            EventEnvelope<Object> envelope = new EventEnvelope<>(metadata, payload);
            return envelope;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to deserialize EventEnvelope from Kafka topic=" + topic, ex);
        }
    }

    private static ObjectMapper createDefaultMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static EventPayloadRegistry createDefaultRegistry() {
        DefaultEventPayloadRegistry defaultRegistry = new DefaultEventPayloadRegistry();
        SharedEventCatalog.registerAll(defaultRegistry);
        return defaultRegistry;
    }

    private static String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String value = field.asText();
        return value == null || value.isBlank() ? null : value;
    }
}
