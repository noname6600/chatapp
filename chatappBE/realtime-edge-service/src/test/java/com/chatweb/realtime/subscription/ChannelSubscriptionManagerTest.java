package com.chatweb.realtime.subscription;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.routing.command.IChatCommandRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelSubscriptionManagerTest {

    @Mock
    private IChatCommandRouter chatCommandRouter;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChannelSubscriptionManager manager;

    @BeforeEach
    void setUp() {
        manager = new ChannelSubscriptionManager(chatCommandRouter, redisTemplate);
    }

    @Test
    void roomChannel_requiresVerifiedMembership() {
        RealtimeSession session = RealtimeSession.create(UUID.randomUUID());
        UUID roomId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(chatCommandRouter.canAccessRoom("token", roomId)).thenReturn(true);

        boolean allowed = manager.isAuthorized(session, "room:" + roomId, "token");

        assertThat(allowed).isTrue();
        verify(chatCommandRouter).canAccessRoom("token", roomId);
    }

    @Test
    void presenceChannel_deniedWhenMembershipCheckFails() {
        RealtimeSession session = RealtimeSession.create(UUID.randomUUID());
        UUID roomId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(chatCommandRouter.canAccessRoom("token", roomId)).thenReturn(false);

        boolean allowed = manager.isAuthorized(session, "presence:" + roomId, "token");

        assertThat(allowed).isFalse();
        verify(chatCommandRouter).canAccessRoom("token", roomId);
    }

    @Test
    void typingChannel_requiresVerifiedMembership() {
        RealtimeSession session = RealtimeSession.create(UUID.randomUUID());
        UUID roomId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(chatCommandRouter.canAccessRoom("token", roomId)).thenReturn(true);

        boolean allowed = manager.isAuthorized(session, "typing:" + roomId, "token");

        assertThat(allowed).isTrue();
        verify(chatCommandRouter).canAccessRoom("token", roomId);
    }

    @Test
    void userChannel_allowsOnlyOwnUserId() {
        UUID userId = UUID.randomUUID();
        RealtimeSession session = RealtimeSession.create(userId);

        boolean allowed = manager.isAuthorized(session, "user:" + userId, "token");

        assertThat(allowed).isTrue();
    }
}
