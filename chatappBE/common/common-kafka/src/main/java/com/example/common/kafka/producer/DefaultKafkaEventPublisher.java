package com.example.common.kafka.producer;

import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;
import com.example.common.event.validation.EventContractValidator;
import com.example.common.kafka.exception.KafkaMessagingException;
import com.example.common.kafka.flow.KafkaEventRoutingContext;
import com.example.common.kafka.observability.KafkaEventObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Default implementation of {@link KafkaEventPublisher}.
 *
 * <p>Validates the envelope metadata before sending, delegates to {@link KafkaTemplate},
 * and delegates observability to {@link KafkaEventObserver}.
 *
 * @since 2.2
 */
@RequiredArgsConstructor
public class DefaultKafkaEventPublisher implements KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaEventObserver logger;

    @Override
    public void publish(String topic, String key, EventEnvelope<?> envelope) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Kafka topic must not be null or blank");
        }
        KafkaEventRoutingContext context = KafkaEventRoutingContext.of(topic, key, envelope);
        EventMetadata metadata = envelope == null ? null : envelope.metadata();

        // Stage: VALIDATE — fail fast on contract violations before any I/O
        try {
            EventContractValidator.validateEventNameOrThrow(metadata == null ? null : metadata.getEventType());
            EventContractValidator.validateIdentityOrThrow(
                    metadata == null ? null : metadata.getEventId(),
                    metadata == null ? null : metadata.getCorrelationId(),
                    metadata == null ? null : metadata.getSourceService(),
                    metadata == null ? null : metadata.getCreatedAt());
        } catch (IllegalArgumentException ex) {
            logger.logError(context, envelope, ex);
            throw new KafkaMessagingException(topic, "Failed at Kafka lifecycle stage VALIDATE", ex);
        }

        // Stage: PUBLISH — send via KafkaTemplate
        try {
            kafkaTemplate.send(topic, key, envelope)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            logger.logPublish(context, envelope);
                        }
                    })
                    .join();
        } catch (Exception ex) {
            logger.logError(context, envelope, ex);
            throw new KafkaMessagingException(topic, "Failed at Kafka lifecycle stage PUBLISH", ex);
        }
    }
}
