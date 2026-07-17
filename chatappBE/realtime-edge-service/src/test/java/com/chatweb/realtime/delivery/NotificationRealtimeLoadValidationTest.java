package com.chatweb.realtime.delivery;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationRealtimeLoadValidationTest {

    @Test
    void stagingSizedNotificationFanout_smokeLoad() throws Exception {
        RealtimeSessionRegistry registry = new RealtimeSessionRegistry();
        RealtimeWebSocketSessionStore store = new RealtimeWebSocketSessionStore();
        ObjectMapper objectMapper = new ObjectMapper();
        WebSocketOutboundDeliveryQueue outboundQueue = new WebSocketOutboundDeliveryQueue(2000, 4);
        NotificationRealtimeDeliveryService service = new NotificationRealtimeDeliveryService(registry, store, objectMapper, outboundQueue);

        UUID userId = UUID.randomUUID();
        String notificationChannel = "notification:" + userId;
        List<WebSocketSession> sockets = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            RealtimeSession session = RealtimeSession.create(userId);
            session.subscribe(notificationChannel);
            registry.register(session);

            WebSocketSession socket = mock(WebSocketSession.class);
            when(socket.isOpen()).thenReturn(true);
            doNothing().when(socket).sendMessage(any(TextMessage.class));
            store.register(session.getSessionId(), socket);
            sockets.add(socket);
        }

        long startNanos = System.nanoTime();
        int iterations = 200;
        int deliveredSum = 0;
        for (int i = 0; i < iterations; i++) {
            deliveredSum += service.deliverToUser(
                    userId,
                    i % 2 == 0 ? "notification.new" : "notification.unread.count.updated",
                    "event-" + i,
                    Map.of("index", i)
            );
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        long elapsedMillis = elapsedNanos / 1_000_000;
        long averageMicros = elapsedNanos / iterations / 1_000;

        System.out.println("[NOTI-LOAD] sessions=20 iterations=" + iterations
                + " totalDelivered=" + deliveredSum
                + " elapsedMs=" + elapsedMillis
                + " avgPerDeliveryUs=" + averageMicros);

        assertThat(deliveredSum).isEqualTo(20 * iterations);
        outboundQueue.shutdown();
    }
}
