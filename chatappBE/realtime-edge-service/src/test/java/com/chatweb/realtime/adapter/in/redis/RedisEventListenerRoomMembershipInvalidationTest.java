package com.chatweb.realtime.adapter.in.redis;

import com.chatweb.realtime.delivery.ChatRealtimeDeliveryService;
import com.chatweb.realtime.delivery.NotificationRealtimeDeliveryService;
import com.chatweb.realtime.delivery.PresenceRealtimeDeliveryService;
import com.chatweb.realtime.subscription.ChannelSubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RedisEventListenerRoomMembershipInvalidationTest {

    @Test
    void memberRemovedEvent_invalidatesTargetedRoomAccessCache() {
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

        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String channel = "realtime.chat.room." + roomId;
        String payload = "{\"metadata\":{\"eventType\":\"chat.room.member.removed\",\"eventId\":\"evt-2\"},\"payload\":{\"roomId\":\""
                + roomId + "\",\"userId\":\"" + userId + "\"}}";

        listener.onMessage(
                new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8)),
                null
        );

        verify(subscriptionManager).invalidateRoomAccess(userId, roomId);
        verify(chatDeliveryService).deliverRoom(org.mockito.ArgumentMatchers.eq(roomId.toString()), org.mockito.ArgumentMatchers.eq("chat.room.member.removed"), org.mockito.ArgumentMatchers.eq("evt-2"), any());
    }

    @Test
    void nonMembershipChatEvent_doesNotInvalidateCache() {
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

        UUID roomId = UUID.randomUUID();
        String channel = "realtime.chat.room." + roomId;
        String payload = "{\"metadata\":{\"eventType\":\"chat.message.sent\",\"eventId\":\"evt-3\"},\"payload\":{\"id\":\"m1\"}}";

        listener.onMessage(
                new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), payload.getBytes(StandardCharsets.UTF_8)),
                null
        );

        verify(subscriptionManager, never()).invalidateRoomAccess(any(), any());
        verify(chatDeliveryService).deliverRoom(org.mockito.ArgumentMatchers.eq(roomId.toString()), org.mockito.ArgumentMatchers.eq("chat.message.sent"), org.mockito.ArgumentMatchers.eq("evt-3"), any());
    }
}
