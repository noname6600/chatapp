package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.integration.notification.NotificationRequestedPayload;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.integration.notification.NotificationEventType;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationEventProducer {

    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    public void publishRequested(Notification noti) {
        NotificationRequestedPayload payload =
                new NotificationRequestedPayload(
                        noti.getId(),
                        noti.getUserId(),
                        noti.getSenderName(),
                        noti.getPreview(),
                        noti.getType() == null ? null : noti.getType().name()
                );

        String eventId = UUID.randomUUID().toString();
        EventMetadata metadata = new EventMetadata(
            eventId,
            NotificationEventType.NOTIFICATION_REQUESTED.value(),
            sourceService,
            Instant.now(),
            TraceContext.correlationIdOrEventId(eventId)
        );

        EventEnvelope<NotificationRequestedPayload> envelope = new EventEnvelope<>(metadata, payload);

        kafkaEventPublisher.publish(
            NotificationEventType.NOTIFICATION_REQUESTED.value(),
            noti.getUserId().toString(),
            envelope
        );
    }
}

