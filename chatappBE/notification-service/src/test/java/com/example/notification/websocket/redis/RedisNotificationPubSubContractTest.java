package com.example.notification.websocket.redis;

import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.notification.constants.NotificationRedisChannels;
import com.example.notification.websocket.WebSocketUserBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisNotificationPubSubContractTest {

    @Test
    void publisher_serializesPayloadAndUsesSharedChannelConvention() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RedisNotificationPublisher publisher = new RedisNotificationPublisher(redisTemplate, objectMapper);

        UUID userId = UUID.randomUUID();
        RealtimeWsEvent event = RealtimeWsEvent.builder()
                .type("notification.new")
                .payload(Map.of("id", "n1"))
                .build();

        publisher.publish(userId, event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(NotificationRedisChannels.userChannel(userId)), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("\"type\":\"notification.new\"");
    }

    @Test
    void subscriber_deserializesPayloadAndRoutesUsingSharedChannelPrefix() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        WebSocketUserBroadcaster broadcaster = mock(WebSocketUserBroadcaster.class);
        RedisNotificationSubscriber subscriber = new RedisNotificationSubscriber(objectMapper, broadcaster);

        UUID userId = UUID.randomUUID();
        String channel = NotificationRedisChannels.userChannel(userId);
        String json = objectMapper.writeValueAsString(RealtimeWsEvent.builder()
                .type("notification.new")
                .payload(Map.of("count", 1))
                .build());

        subscriber.onMessage(
                new DefaultMessage(
                        channel.getBytes(StandardCharsets.UTF_8),
                        json.getBytes(StandardCharsets.UTF_8)
                ),
                null
        );

        ArgumentCaptor<RealtimeWsEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeWsEvent.class);
        verify(broadcaster).sendToUser(eq(userId), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("notification.new");
    }
}
