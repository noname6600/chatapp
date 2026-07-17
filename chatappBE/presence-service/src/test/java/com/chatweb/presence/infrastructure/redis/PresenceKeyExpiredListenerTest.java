package com.chatweb.presence.infrastructure.redis;

import com.chatweb.presence.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceKeyExpiredListenerTest {

    @Mock
    private PresenceService presenceService;

    private PresenceKeyExpiredListener listener;

    @BeforeEach
    void setUp() {
        listener = new PresenceKeyExpiredListener(presenceService);
    }

    @Test
    void onMessage_handlesPresenceUserExpiryKey() {
        UUID userId = UUID.randomUUID();
        Message message = new DefaultMessage(("presence::user:" + userId).getBytes(StandardCharsets.UTF_8), new byte[0]);

        listener.onMessage(message, new byte[0]);

        verify(presenceService).handleUserOfflineByTTL(userId);
    }

    @Test
    void onMessage_ignoresUnrelatedKey() {
        Message message = new DefaultMessage("room:123".getBytes(StandardCharsets.UTF_8), new byte[0]);

        listener.onMessage(message, new byte[0]);

        verify(presenceService, never()).handleUserOfflineByTTL(org.mockito.ArgumentMatchers.any());
    }
}
