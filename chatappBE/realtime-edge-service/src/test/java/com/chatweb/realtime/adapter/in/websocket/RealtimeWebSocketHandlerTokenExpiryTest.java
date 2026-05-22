package com.chatweb.realtime.adapter.in.websocket;

import com.chatweb.realtime.adapter.out.chat.RestChatCommandRouter;
import com.chatweb.realtime.adapter.out.presence.EdgePresenceLifecycleBridge;
import com.chatweb.realtime.adapter.out.presence.PresenceDomainClient;
import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.connection.RealtimeSessionRegistry;
import com.chatweb.realtime.connection.RealtimeWebSocketSessionStore;
import com.chatweb.realtime.delivery.WebSocketOutboundDeliveryQueue;
import com.chatweb.realtime.routing.CommandDispatcher;
import com.chatweb.realtime.subscription.ChannelSubscriptionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketHandlerTokenExpiryTest {

    @Mock
    private RealtimeSessionRegistry sessionRegistry;

    @Mock
    private RealtimeWebSocketSessionStore webSocketSessionStore;

    @Mock
    private ChannelSubscriptionManager subscriptionManager;

    @Mock
    private PresenceDomainClient presenceDomainClient;

    @Mock
    private EdgePresenceLifecycleBridge presenceLifecycleBridge;

    @Mock
    private RestChatCommandRouter chatCommandRouter;

    @Mock
    private CommandDispatcher commandDispatcher;

    @Mock
    private WebSocketSession webSocketSession;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RealtimeSideEffectQueue sideEffectQueue;

    @Mock
    private WebSocketOutboundDeliveryQueue outboundDeliveryQueue;

    @Test
    void expiredToken_midSession_closesWithTokenExpiredReason() throws Exception {
        RealtimeWebSocketHandler handler = new RealtimeWebSocketHandler(
                sessionRegistry,
                webSocketSessionStore,
                subscriptionManager,
                presenceDomainClient,
                presenceLifecycleBridge,
                chatCommandRouter,
                commandDispatcher,
                new ObjectMapper(),
                redisTemplate,
                sideEffectQueue,
                outboundDeliveryQueue
        );

        UUID userId = UUID.randomUUID();
        RealtimeSession realtimeSession = RealtimeSession.create(userId);
        realtimeSession.setSessionId("rt-session-expired");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("realtimeSession", realtimeSession);
        attributes.put("realtimeEndpoint", "/realtime");
        attributes.put("accessTokenRef", "token-ref-expired");

        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ws:session-token:token-ref-expired")).thenReturn(null);

        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"JOIN\",\"roomId\":\"" + UUID.randomUUID() + "\"}"));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload()).contains("TOKEN_EXPIRED");
        verify(webSocketSession).close(any(CloseStatus.class));
    }
}
