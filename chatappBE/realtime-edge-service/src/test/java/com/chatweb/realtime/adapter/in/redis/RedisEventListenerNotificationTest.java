package com.chatweb.realtime.adapter.in.redis;

import com.chatweb.realtime.delivery.ChatRealtimeDeliveryService;
import com.chatweb.realtime.delivery.NotificationRealtimeDeliveryService;
import com.chatweb.realtime.delivery.PresenceRealtimeDeliveryService;
import com.chatweb.realtime.subscription.ChannelSubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RedisEventListenerNotificationTest {

    @Test
    void notificationRedisEvent_isRoutedToUserDelivery() throws Exception {
        ChatRealtimeDeliveryService chatDeliveryService = mock(ChatRealtimeDeliveryService.class);
        NotificationRealtimeDeliveryService notificationDeliveryService = mock(NotificationRealtimeDeliveryService.class);
        PresenceRealtimeDeliveryService presenceDeliveryService = mock(PresenceRealtimeDeliveryService.class);
        ChannelSubscriptionManager subscriptionManager = mock(ChannelSubscriptionManager.class);
        RedisEventListener listener = new RedisEventListener(
                chatDeliveryService,
                notificationDeliveryService,
                presenceDeliveryService,
            subscriptionManager,
                new ObjectMapper()
        );

        UUID userId = UUID.randomUUID();
        String channel = "realtime.notification.user." + userId;
        String payload = "{\"metadata\":{\"eventType\":\"notification.new\",\"eventId\":\"evt-1\"},\"payload\":{\"id\":\"n1\"}}";

        listener.onMessage(
                new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8)),
                null
        );

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(notificationDeliveryService).deliverToUser(org.mockito.ArgumentMatchers.eq(userId), eventTypeCaptor.capture(), org.mockito.ArgumentMatchers.eq("evt-1"), bodyCaptor.capture());
        verify(subscriptionManager, never()).invalidateRoomAccess(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(eventTypeCaptor.getValue()).isEqualTo("notification.new");
        assertThat(bodyCaptor.getValue().toString()).contains("n1");
    }
}
