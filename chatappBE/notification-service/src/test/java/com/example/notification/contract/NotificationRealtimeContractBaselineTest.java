package com.example.notification.contract;

import com.example.common.integration.realtime.RealtimeContractVersions;
import com.example.common.redis.channel.RedisChannels;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.notification.constants.NotificationRedisChannels;
import com.example.notification.websocket.NotificationWebSocketPublisher;
import com.example.notification.websocket.redis.RedisNotificationPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationRealtimeContractBaselineTest {

    @Test
    void notificationRedisChannelConstants_matchCurrentContract() {
        assertThat(NotificationRedisChannels.NOTIFICATION_USER_PREFIX).isEqualTo(RedisChannels.NOTIFICATION_USER_PREFIX);
        assertThat(NotificationRedisChannels.NOTIFICATION_USER_PATTERN).isEqualTo(RedisChannels.NOTIFICATION_USER_PATTERN);
        assertThat(RealtimeContractVersions.NOTIFICATION_KAFKA_EVENTS).isEqualTo("v1");
        assertThat(RealtimeContractVersions.NOTIFICATION_REDIS_FANOUT).isEqualTo("v1");
    }

    @Test
    void notificationPublisher_emitsCurrentWebsocketTypeLiterals() {
        RedisNotificationPublisher redisPublisher = mock(RedisNotificationPublisher.class);
        NotificationWebSocketPublisher publisher = new NotificationWebSocketPublisher(redisPublisher);
        UUID userId = UUID.randomUUID();

        publisher.publishNotificationNew(userId, null);

        ArgumentCaptor<RealtimeWsEvent> captor = ArgumentCaptor.forClass(RealtimeWsEvent.class);
        verify(redisPublisher).publish(org.mockito.ArgumentMatchers.eq(userId), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("notification.new");
    }
}
