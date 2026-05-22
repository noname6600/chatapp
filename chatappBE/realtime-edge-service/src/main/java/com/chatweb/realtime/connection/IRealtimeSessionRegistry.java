package com.chatweb.realtime.connection;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Realtime session registry abstraction used by the edge delivery/runtime path.
 *
 * Implementations:
 * - In-memory (default local/dev)
 * - Redis-backed (multi-instance metadata foundation)
 */
public interface IRealtimeSessionRegistry {

    void register(RealtimeSession session);

    void unregister(String sessionId);

    Optional<RealtimeSession> findBySessionId(String sessionId);

    Collection<RealtimeSession> findByUserId(UUID userId);

    Collection<RealtimeSession> findByChannel(String channel);

    default Collection<RealtimeSession> findByUserIdOwnedByCurrentInstance(UUID userId) {
        return findByUserId(userId);
    }

    default Collection<RealtimeSession> findByChannelOwnedByCurrentInstance(String channel) {
        return findByChannel(channel);
    }

    void addSubscription(String sessionId, String channel);

    void removeSubscription(String sessionId, String channel);

    default void refreshSessionLease(String sessionId) {
        // no-op by default; implementations can persist lease/freshness
    }

    default int evictStaleSessions() {
        return 0;
    }

    default int cleanupOrphanIndexes() {
        return 0;
    }

    int getActiveSessionCount();

    default int getActiveLocalSessionCount() {
        return getActiveSessionCount();
    }

    int getActiveUserCount();

    String getCurrentInstanceId();
}
