package com.chatweb.realtime.delivery;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

class NotificationRealtimeDeliveryServiceTest {

    @Test
    void deliversToMultipleSessionsAndAfterReconnect() throws Exception {
        RealtimeSessionRegistry registry = new RealtimeSessionRegistry();
        RealtimeWebSocketSessionStore store = new RealtimeWebSocketSessionStore();
        ObjectMapper objectMapper = new ObjectMapper();
        WebSocketOutboundDeliveryQueue outboundQueue = new WebSocketOutboundDeliveryQueue(200, 2);
        NotificationRealtimeDeliveryService service = new NotificationRealtimeDeliveryService(registry, store, objectMapper, outboundQueue);

        UUID userId = UUID.randomUUID();
        String notificationChannel = "notification:" + userId;

        RealtimeSession firstSession = RealtimeSession.create(userId);
        firstSession.subscribe(notificationChannel);
        registry.register(firstSession);
        WebSocketSession firstSocket = mock(WebSocketSession.class);
        when(firstSocket.isOpen()).thenReturn(true);
        doNothing().when(firstSocket).sendMessage(any(TextMessage.class));
        store.register(firstSession.getSessionId(), firstSocket);

        RealtimeSession secondSession = RealtimeSession.create(userId);
        secondSession.subscribe(notificationChannel);
        registry.register(secondSession);
        WebSocketSession secondSocket = mock(WebSocketSession.class);
        when(secondSocket.isOpen()).thenReturn(true);
        doNothing().when(secondSocket).sendMessage(any(TextMessage.class));
        store.register(secondSession.getSessionId(), secondSocket);

        int delivered = service.deliverToUser(userId, "notification.new", "event-1", java.util.Map.of("id", "n1"));
        assertThat(delivered).isEqualTo(2);
        verify(firstSocket, timeout(1000)).sendMessage(any(TextMessage.class));
        verify(secondSocket, timeout(1000)).sendMessage(any(TextMessage.class));

        registry.unregister(firstSession.getSessionId());
        store.unregister(firstSession.getSessionId());

        int deliveredAfterReconnect = service.deliverToUser(userId, "notification.unread.count.updated", "event-2", java.util.Map.of("unreadCount", 3));
        assertThat(deliveredAfterReconnect).isEqualTo(1);
        verify(secondSocket, timeout(1000).times(2)).sendMessage(any(TextMessage.class));

        outboundQueue.shutdown();
    }
}
