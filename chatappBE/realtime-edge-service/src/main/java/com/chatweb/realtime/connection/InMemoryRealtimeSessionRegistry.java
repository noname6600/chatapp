package com.chatweb.realtime.connection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory registry implementation for local/dev/single-instance runtime.
 */
@Component
@ConditionalOnProperty(name = "realtime.session-registry.mode", havingValue = "in-memory", matchIfMissing = true)
@Slf4j
public class InMemoryRealtimeSessionRegistry implements IRealtimeSessionRegistry {

    private final String instanceId;
    private final Map<String, RealtimeSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> channelSessions = new ConcurrentHashMap<>();

    public InMemoryRealtimeSessionRegistry(@Value("${realtime.session-registry.instance-id:local-instance}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public void register(RealtimeSession session) {
        if (session == null) {
            return;
        }
        if (session.getInstanceId() == null || session.getInstanceId().isBlank()) {
            session.setInstanceId(instanceId);
        }

        sessions.put(session.getSessionId(), session);
        userSessions.computeIfAbsent(session.getUserId(), k -> ConcurrentHashMap.newKeySet())
                .add(session.getSessionId());

        for (String channel : session.getSubscriptions()) {
            channelSessions.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet())
                    .add(session.getSessionId());
        }

        log.info("[REGISTRY][MEM] Registered session={} user={} instance={} totalSessions={}",
                session.getSessionId(), session.getUserId(), session.getInstanceId(), sessions.size());
    }

    @Override
    public void unregister(String sessionId) {
        RealtimeSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }

        Set<String> userSet = userSessions.get(session.getUserId());
        if (userSet != null) {
            userSet.remove(sessionId);
            if (userSet.isEmpty()) {
                userSessions.remove(session.getUserId());
            }
        }

        for (String channel : session.getSubscriptions()) {
            Set<String> channelSet = channelSessions.get(channel);
            if (channelSet != null) {
                channelSet.remove(sessionId);
                if (channelSet.isEmpty()) {
                    channelSessions.remove(channel);
                }
            }
        }

        log.info("[REGISTRY][MEM] Unregistered session={} user={} totalSessions={}",
                sessionId, session.getUserId(), sessions.size());
    }

    @Override
    public Optional<RealtimeSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public Collection<RealtimeSession> findByUserId(UUID userId) {
        Set<String> sessionIds = userSessions.get(userId);
        if (sessionIds == null) {
            return Set.of();
        }
        return sessionIds.stream()
                .map(sessions::get)
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<RealtimeSession> findByChannel(String channel) {
        Set<String> sessionIds = channelSessions.get(channel);
        if (sessionIds == null) {
            return Set.of();
        }
        return sessionIds.stream()
                .map(sessions::get)
                .filter(s -> s != null)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<RealtimeSession> findByUserIdOwnedByCurrentInstance(UUID userId) {
        return findByUserId(userId);
    }

    @Override
    public Collection<RealtimeSession> findByChannelOwnedByCurrentInstance(String channel) {
        return findByChannel(channel);
    }

    @Override
    public void addSubscription(String sessionId, String channel) {
        RealtimeSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        session.subscribe(channel);
        session.setLastActivityAt(java.time.Instant.now());
        channelSessions.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    @Override
    public void removeSubscription(String sessionId, String channel) {
        RealtimeSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        session.unsubscribe(channel);
        session.setLastActivityAt(java.time.Instant.now());
        Set<String> channelSet = channelSessions.get(channel);
        if (channelSet != null) {
            channelSet.remove(sessionId);
            if (channelSet.isEmpty()) {
                channelSessions.remove(channel);
            }
        }
    }

    @Override
    public void refreshSessionLease(String sessionId) {
        RealtimeSession session = sessions.get(sessionId);
        if (session != null) {
            session.setLastActivityAt(java.time.Instant.now());
        }
    }

    @Override
    public int getActiveSessionCount() {
        return sessions.size();
    }

    @Override
    public int getActiveLocalSessionCount() {
        return sessions.size();
    }

    @Override
    public int getActiveUserCount() {
        return userSessions.size();
    }

    @Override
    public String getCurrentInstanceId() {
        return instanceId;
    }
}
