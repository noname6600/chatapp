package com.example.notification.websocket;

import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.websocket.redis.RedisNotificationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketPublisherTest {

    @Mock
    private RedisNotificationPublisher redisNotificationPublisher;

    @InjectMocks
    private NotificationWebSocketPublisher publisher;

    @Test
    void NotificationWebSocketPublisher_sendsToCorrectUserDestination() {
        UUID userId = UUID.randomUUID();
        NotificationResponse payload = NotificationResponse.builder()
                .id(UUID.randomUUID())
                .type("MESSAGE")
                .build();

        publisher.publishNotificationNew(userId, payload);

        ArgumentCaptor<RealtimeWsEvent> messageCaptor = ArgumentCaptor.forClass(RealtimeWsEvent.class);
        verify(redisNotificationPublisher).publish(eq(userId), messageCaptor.capture());

        RealtimeWsEvent sent = messageCaptor.getValue();
        assertThat(sent.getType()).isEqualTo("notification.new");
        assertThat(sent.getPayload()).isEqualTo(payload);
    }
}
