package com.example.notification.websocket;

import com.example.common.websocket.dto.WsOutgoingMessage;
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

        ArgumentCaptor<WsOutgoingMessage> messageCaptor = ArgumentCaptor.forClass(WsOutgoingMessage.class);
        verify(redisNotificationPublisher).publish(eq(userId), messageCaptor.capture());

        WsOutgoingMessage sent = messageCaptor.getValue();
        assertThat(sent.getType()).isEqualTo("NOTIFICATION_NEW");
        assertThat(sent.getPayload()).isEqualTo(payload);
    }
}
