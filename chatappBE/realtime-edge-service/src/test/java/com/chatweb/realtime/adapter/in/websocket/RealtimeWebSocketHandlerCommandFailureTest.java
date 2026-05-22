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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketHandlerCommandFailureTest {

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
    void failedCommandDispatch_returnsCommandFailedErrorFrame() throws Exception {
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
        realtimeSession.setSessionId("rt-session-command-failure");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("realtimeSession", realtimeSession);
        attributes.put("realtimeEndpoint", "/realtime");
        attributes.put("accessTokenRef", "token-ref-5");

        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ws:session-token:token-ref-5")).thenReturn("access-token");
        when(commandDispatcher.dispatch(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"notification.command\",\"command\":\"mark-read\",\"requestId\":\"req-1\"}"));

        org.mockito.ArgumentCaptor<TextMessage> messageCaptor = org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        org.mockito.Mockito.verify(webSocketSession).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload()).contains("COMMAND_FAILED");
        assertThat(messageCaptor.getValue().getPayload()).contains("Could not process command");
    }
}
