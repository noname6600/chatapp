package com.example.notification.kafka;

import com.example.common.integration.notification.NotificationRequestedPayload;
import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.integration.kafka.event.NotificationRequestedEvent;
import com.example.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

        kafkaEventPublisher.publish(
                KafkaTopics.NOTIFICATION_REQUESTED,
                noti.getUserId().toString(),
                NotificationRequestedEvent.from(sourceService, payload)
        );
    }
}

