package com.chatweb.realtime.connection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade for the active realtime session registry backend.
 *
 * Keeps legacy call sites unchanged while allowing backend selection by config.
 */
@Component
public class RealtimeSessionRegistry implements IRealtimeSessionRegistry {

    private final IRealtimeSessionRegistry delegate;

    /**
     * Test fallback constructor to preserve existing unit tests that instantiate directly.
     */
    public RealtimeSessionRegistry() {
        this.delegate = new InMemoryRealtimeSessionRegistry("local-instance");
    }

    @Autowired
    public RealtimeSessionRegistry(IRealtimeSessionRegistry delegate) {
        this.delegate = delegate;
    }

    @Override
    public void register(RealtimeSession session) {
        delegate.register(session);
    }

    @Override
    public void unregister(String sessionId) {
        delegate.unregister(sessionId);
    }

    public Optional<RealtimeSession> findBySessionId(String sessionId) {
        return delegate.findBySessionId(sessionId);
    }

    @Override
    public Collection<RealtimeSession> findByUserId(UUID userId) {
        return delegate.findByUserId(userId);
    }

    @Override
    public Collection<RealtimeSession> findByChannel(String channel) {
        return delegate.findByChannel(channel);
    }

    @Override
    public Collection<RealtimeSession> findByUserIdOwnedByCurrentInstance(UUID userId) {
        return delegate.findByUserIdOwnedByCurrentInstance(userId);
    }

    @Override
    public Collection<RealtimeSession> findByChannelOwnedByCurrentInstance(String channel) {
        return delegate.findByChannelOwnedByCurrentInstance(channel);
    }

    @Override
    public void addSubscription(String sessionId, String channel) {
        delegate.addSubscription(sessionId, channel);
    }

    @Override
    public void removeSubscription(String sessionId, String channel) {
        delegate.removeSubscription(sessionId, channel);
    }

    @Override
    public void refreshSessionLease(String sessionId) {
        delegate.refreshSessionLease(sessionId);
    }

    @Override
    public int evictStaleSessions() {
        return delegate.evictStaleSessions();
    }

    @Override
    public int cleanupOrphanIndexes() {
        return delegate.cleanupOrphanIndexes();
    }

    @Override
    public int getActiveSessionCount() {
        return delegate.getActiveSessionCount();
    }

    @Override
    public int getActiveLocalSessionCount() {
        return delegate.getActiveLocalSessionCount();
    }

    @Override
    public int getActiveUserCount() {
        return delegate.getActiveUserCount();
    }

    @Override
    public String getCurrentInstanceId() {
        return delegate.getCurrentInstanceId();
    }
}
