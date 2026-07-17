package com.chatweb.realtime.connection;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory representation of a connected realtime client session.
 *
 * Tracks user identity, connection state, active subscriptions.
 */
@Data
@Builder
@Slf4j
public class RealtimeSession {
    private String sessionId;
    private UUID userId;
    private String instanceId;
    private Set<String> subscriptions;
    private Instant connectedAt;
    private Instant lastActivityAt;
    private Instant leaseExpiresAt;
    private ConnectionState state;

    public enum ConnectionState {
        CONNECTED, RECONNECTING, DISCONNECTED
    }

    public static RealtimeSession create(UUID userId) {
        return RealtimeSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(userId)
                .subscriptions(new HashSet<>())
                .connectedAt(Instant.now())
                .lastActivityAt(Instant.now())
                .state(ConnectionState.CONNECTED)
                .build();
    }

    public synchronized void subscribe(String channel) {
        this.subscriptions.add(channel);
        this.lastActivityAt = Instant.now();
        log.debug("[SESSION] userId={} subscribed to channel={}", userId, channel);
    }

    public synchronized void unsubscribe(String channel) {
        this.subscriptions.remove(channel);
        this.lastActivityAt = Instant.now();
        log.debug("[SESSION] userId={} unsubscribed from channel={}", userId, channel);
    }

    public synchronized boolean isSubscribedTo(String channel) {
        return this.subscriptions.contains(channel);
    }

    public synchronized int getSubscriptionCount() {
        return this.subscriptions.size();
    }

    public void updateActivity() {
        this.lastActivityAt = Instant.now();
    }
}
