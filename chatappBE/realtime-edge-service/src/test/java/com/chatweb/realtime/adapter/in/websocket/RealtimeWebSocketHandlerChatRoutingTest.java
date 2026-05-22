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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeWebSocketHandlerChatRoutingTest {

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
    void chatJoin_subscribesLocallyWhenRoomAccessIsVerified() throws Exception {
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
        UUID roomId = UUID.randomUUID();
        RealtimeSession realtimeSession = RealtimeSession.create(userId);
        realtimeSession.setSessionId("rt-session-1");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("realtimeSession", realtimeSession);
        attributes.put("realtimeEndpoint", "/realtime");
        attributes.put("accessTokenRef", "token-ref-1");

        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ws:session-token:token-ref-1")).thenReturn("chat-token");
        when(subscriptionManager.isAuthorized(realtimeSession, "room:" + roomId, "chat-token")).thenReturn(true);

        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\"}"));

        verify(subscriptionManager).isAuthorized(realtimeSession, "room:" + roomId, "chat-token");
        verify(sessionRegistry).addSubscription("rt-session-1", "room:" + roomId);
        verify(commandDispatcher, never()).dispatch(any(), any(), any());
        verify(webSocketSession, never()).sendMessage(any());
    }

    @Test
    void chatJoin_deniedWhenRoomAccessCheckFails() throws Exception {
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
        UUID roomId = UUID.randomUUID();
        RealtimeSession realtimeSession = RealtimeSession.create(userId);
        realtimeSession.setSessionId("rt-session-2");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("realtimeSession", realtimeSession);
        attributes.put("realtimeEndpoint", "/realtime");
        attributes.put("accessTokenRef", "token-ref-2");

        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ws:session-token:token-ref-2")).thenReturn("chat-token");
        when(subscriptionManager.isAuthorized(realtimeSession, "room:" + roomId, "chat-token")).thenReturn(false);

        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\"}"));

        verify(subscriptionManager).isAuthorized(realtimeSession, "room:" + roomId, "chat-token");
        verify(sessionRegistry, never()).addSubscription("rt-session-2", "room:" + roomId);
        verify(webSocketSession).sendMessage(any());
    }

    @Test
    void presenceRoomJoin_subscribesWhenPresenceAndTypingChannelsAuthorized() throws Exception {
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
        UUID roomId = UUID.randomUUID();
        RealtimeSession realtimeSession = RealtimeSession.create(userId);
        realtimeSession.setSessionId("rt-session-presence-1");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("realtimeSession", realtimeSession);
        attributes.put("realtimeEndpoint", "/realtime");
        attributes.put("accessTokenRef", "token-ref-3");

        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ws:session-token:token-ref-3")).thenReturn("presence-token");
        when(sideEffectQueue.submit(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        });
        when(subscriptionManager.isAuthorized(realtimeSession, "presence:" + roomId, "presence-token")).thenReturn(true);
        when(subscriptionManager.isAuthorized(realtimeSession, "typing:" + roomId, "presence-token")).thenReturn(true);
        when(presenceDomainClient.roomSnapshot("presence-token", roomId)).thenReturn(new ObjectMapper().createArrayNode());

        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"presence.room.join\",\"roomId\":\"" + roomId + "\"}"));

        verify(subscriptionManager).isAuthorized(realtimeSession, "presence:" + roomId, "presence-token");
        verify(subscriptionManager).isAuthorized(realtimeSession, "typing:" + roomId, "presence-token");
        verify(sessionRegistry).addSubscription("rt-session-presence-1", "presence:" + roomId);
        verify(sessionRegistry).addSubscription("rt-session-presence-1", "typing:" + roomId);
        verify(presenceDomainClient).joinRoom("presence-token", roomId);
    }

    @Test
    void presenceRoomJoin_deniedWhenPresenceOrTypingChannelUnauthorized() throws Exception {
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
        UUID roomId = UUID.randomUUID();
        RealtimeSession realtimeSession = RealtimeSession.create(userId);
        realtimeSession.setSessionId("rt-session-presence-2");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("realtimeSession", realtimeSession);
        attributes.put("realtimeEndpoint", "/realtime");
        attributes.put("accessTokenRef", "token-ref-4");

        when(webSocketSession.getAttributes()).thenReturn(attributes);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ws:session-token:token-ref-4")).thenReturn("presence-token");
        when(subscriptionManager.isAuthorized(realtimeSession, "presence:" + roomId, "presence-token")).thenReturn(false);

        handler.handleTextMessage(webSocketSession, new TextMessage("{\"type\":\"presence.room.join\",\"roomId\":\"" + roomId + "\"}"));

        verify(subscriptionManager).isAuthorized(realtimeSession, "presence:" + roomId, "presence-token");
        verify(subscriptionManager, never()).isAuthorized(realtimeSession, "typing:" + roomId, "presence-token");
        verify(sessionRegistry, never()).addSubscription("rt-session-presence-2", "presence:" + roomId);
        verify(sessionRegistry, never()).addSubscription("rt-session-presence-2", "typing:" + roomId);
        verify(presenceDomainClient, never()).joinRoom("presence-token", roomId);
        verify(webSocketSession).sendMessage(any());
    }
}