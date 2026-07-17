package com.chatweb.common.kafka.producer;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.validation.EventContractValidator;
import com.chatweb.common.kafka.exception.KafkaMessagingException;
import com.chatweb.common.kafka.flow.KafkaEventRoutingContext;
import com.chatweb.common.kafka.observability.KafkaEventObserver;
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

    /**
     * Publishes an event envelope to the given Kafka topic.
     *
    * <p><strong>This is an asynchronous call.</strong> It returns immediately after the send
    * is initiated and records publish completion through the configured observer.
     *
     * @param topic    the Kafka topic to publish to, must not be null or blank
     * @param key      the partition key, may be null
     * @param envelope the event envelope to publish, must not be null
     * @throws KafkaMessagingException if validation fails or the broker send fails
     */
    @Override
    public void publish(String topic, String key, EventEnvelope<?> envelope) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Kafka topic must not be null or blank");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("Kafka envelope must not be null");
        }
        KafkaEventRoutingContext context = KafkaEventRoutingContext.of(topic, key, envelope);
        EventMetadata metadata = envelope.metadata();

        // Stage: VALIDATE â€" fail fast on contract violations before any I/O
        try {
            EventContractValidator.validateEventNameOrThrow(metadata.getEventType());
            EventContractValidator.validateIdentityOrThrow(
                    metadata.getEventId(),
                    metadata.getCorrelationId(),
                    metadata.getSourceService(),
                    metadata.getCreatedAt());
        } catch (IllegalArgumentException ex) {
            logger.logError(context, envelope, ex);
            throw new KafkaMessagingException(topic, "Failed at Kafka lifecycle stage VALIDATE", ex);
        }

        // Stage: PUBLISH â€" fire-and-observe send completion asynchronously
        try {
            kafkaTemplate.send(topic, key, envelope)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            logger.logPublish(context, envelope);
                        } else {
                            logger.logError(context, envelope, ex);
                        }
                    });
        } catch (Exception ex) {
            logger.logError(context, envelope, ex);
            throw new KafkaMessagingException(topic, "Failed at Kafka lifecycle stage PUBLISH", ex);
        }
    }
}
