package com.example.notification.websocket;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final NotificationSessionRegistry sessionRegistry;
    private final MeterRegistry meterRegistry;

    private final AtomicInteger activeConnections = new AtomicInteger(0);

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder("websocket.connections.active", activeConnections, AtomicInteger::get)
                .description("Number of active WebSocket connections")
                .register(meterRegistry);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        activeConnections.incrementAndGet();

        UUID userId = sessionRegistry.getUserId(session);
        log.info("[NOTI] CONNECT user={} session={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
        activeConnections.decrementAndGet();
        log.info("[NOTI] DISCONNECT session={} status={}", session.getId(), status);
    }
}

