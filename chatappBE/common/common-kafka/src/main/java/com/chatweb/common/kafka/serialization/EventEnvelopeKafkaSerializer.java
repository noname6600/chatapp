package com.chatweb.common.kafka.serialization;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.SharedEventCatalog;
import com.chatweb.common.event.validation.EventContractValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serializer;

/**
 * JSON serializer for EventEnvelope values.
 */
public class EventEnvelopeKafkaSerializer implements Serializer<EventEnvelope<?>> {

    private final ObjectMapper objectMapper;

    public EventEnvelopeKafkaSerializer() {
        this(new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    public EventEnvelopeKafkaSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(String topic, EventEnvelope<?> data) {
        if (data == null) {
            throw new IllegalArgumentException("Kafka serializer requires non-null EventEnvelope");
        }
        try {
            validateCanonicalEnvelope(data);
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize EventEnvelope for topic=" + topic, ex);
        }
    }

    private static void validateCanonicalEnvelope(EventEnvelope<?> envelope) {
        EventMetadata metadata = envelope.metadata();
        if (metadata == null) {
            throw new IllegalArgumentException("Kafka envelope metadata must not be null");
        }

        String eventType = metadata.getEventType();
        EventContractValidator.validateEventNameOrThrow(eventType);
    }
}
