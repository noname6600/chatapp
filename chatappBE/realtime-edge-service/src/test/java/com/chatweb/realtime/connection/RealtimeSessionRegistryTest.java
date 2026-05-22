package com.chatweb.realtime.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RealtimeSessionRegistry.
 */
class RealtimeSessionRegistryTest {

    private RealtimeSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RealtimeSessionRegistry();
    }

    @Test
    void register_storesSession() {
        UUID userId = UUID.randomUUID();
        RealtimeSession session = RealtimeSession.create(userId);

        registry.register(session);

        assertThat(registry.findBySessionId(session.getSessionId())).isPresent();
        assertThat(registry.findByUserId(userId)).hasSize(1);
    }

    @Test
    void unregister_removesSession() {
        UUID userId = UUID.randomUUID();
        RealtimeSession session = RealtimeSession.create(userId);
        registry.register(session);

        registry.unregister(session.getSessionId());

        assertThat(registry.findBySessionId(session.getSessionId())).isEmpty();
        assertThat(registry.findByUserId(userId)).isEmpty();
    }

    @Test
    void findByChannel_returnsSubscribedSessions() {
        UUID userId = UUID.randomUUID();
        RealtimeSession session = RealtimeSession.create(userId);
        session.subscribe("room:123");
        registry.register(session);

        Collection<RealtimeSession> result = registry.findByChannel("room:123");

        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getSessionId()).isEqualTo(session.getSessionId());
    }

    @Test
    void getActiveSessionCount_returnsCorrectCount() {
        registry.register(RealtimeSession.create(UUID.randomUUID()));
        registry.register(RealtimeSession.create(UUID.randomUUID()));

        assertThat(registry.getActiveSessionCount()).isEqualTo(2);
    }
}
