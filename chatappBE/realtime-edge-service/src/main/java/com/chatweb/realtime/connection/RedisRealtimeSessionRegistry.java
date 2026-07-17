package com.chatweb.realtime.connection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Redis-backed session registry.
 *
 * Key model:
 * - realtime:session:{sessionId}                       (hash)
 * - realtime:user:sessions:{userId}                    (set)
 * - realtime:subscription:sessions:{subscriptionKey}   (set)
 * - realtime:session:subscriptions:{sessionId}         (set)
 * - realtime:instance:sessions:{instanceId}            (set)
 * - realtime:users                                     (set)
 */
@Component
@ConditionalOnProperty(name = "realtime.session-registry.mode", havingValue = "redis")
@Slf4j
public class RedisRealtimeSessionRegistry implements IRealtimeSessionRegistry {

    private static final String SESSION_PREFIX = "realtime:session:";
    private static final String USER_SESSIONS_PREFIX = "realtime:user:sessions:";
    private static final String SUBSCRIPTION_SESSIONS_PREFIX = "realtime:subscription:sessions:";
    private static final String SESSION_SUBSCRIPTIONS_PREFIX = "realtime:session:subscriptions:";
    private static final String INSTANCE_SESSIONS_PREFIX = "realtime:instance:sessions:";
    private static final String USERS_KEY = "realtime:users";
    private static final String SESSION_COUNT_KEY = "realtime:session:count";

    private final StringRedisTemplate redis;
    private final String instanceId;
    private final Duration leaseTtl;
    private final int cleanupBatchSize;

    public RedisRealtimeSessionRegistry(
            StringRedisTemplate redis,
            @Value("${realtime.session-registry.instance-id:edge-instance}") String instanceId,
            @Value("${realtime.session-registry.lease.ttl-seconds:90}") long leaseTtlSeconds,
            @Value("${realtime.session-registry.lease.cleanup-batch-size:500}") int cleanupBatchSize
    ) {
        this.redis = redis;
        this.instanceId = instanceId;
        this.leaseTtl = Duration.ofSeconds(Math.max(10, leaseTtlSeconds));
        this.cleanupBatchSize = Math.max(1, cleanupBatchSize);
    }

    @Override
    public void register(RealtimeSession session) {
        if (session == null) {
            return;
        }

        if (session.getInstanceId() == null || session.getInstanceId().isBlank()) {
            session.setInstanceId(instanceId);
        }

        String sessionKey = sessionKey(session.getSessionId());
        Map<String, String> values = new HashMap<>();
        values.put("sessionId", session.getSessionId());
        values.put("userId", session.getUserId().toString());
        values.put("instanceId", session.getInstanceId());
        values.put("connectedAt", session.getConnectedAt().toString());
        values.put("lastActivityAt", session.getLastActivityAt().toString());
        values.put("leaseExpiresAt", nextLeaseExpiry().toString());
        values.put("state", session.getState().name());

        redis.opsForHash().putAll(sessionKey, values);
        redis.expire(sessionKey, leaseTtl);
        String userSessionsKey = userSessionsKey(session.getUserId());
        String instanceSessionsKey = instanceSessionsKey(session.getInstanceId());

        redis.opsForSet().add(userSessionsKey, session.getSessionId());
        redis.opsForSet().add(instanceSessionsKey, session.getSessionId());
        redis.opsForSet().add(USERS_KEY, session.getUserId().toString());
        redis.expire(userSessionsKey, leaseTtl);
        redis.expire(instanceSessionsKey, leaseTtl);
        redis.expire(USERS_KEY, leaseTtl);
        redis.opsForValue().increment(SESSION_COUNT_KEY);

        for (String subscription : session.getSubscriptions()) {
            addSubscription(session.getSessionId(), subscription);
        }

        log.info("[REGISTRY][REDIS] Registered session={} user={} instance={}",
                session.getSessionId(), session.getUserId(), session.getInstanceId());
    }

    @Override
    public void unregister(String sessionId) {
        Optional<RealtimeSession> sessionOpt = findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return;
        }

        RealtimeSession session = sessionOpt.get();

        Set<String> subscriptions = redis.opsForSet().members(sessionSubscriptionsKey(sessionId));
        if (subscriptions != null) {
            for (String subscription : subscriptions) {
                redis.opsForSet().remove(subscriptionSessionsKey(subscription), sessionId);
            }
        }

        redis.delete(sessionSubscriptionsKey(sessionId));
        redis.delete(sessionKey(sessionId));
        redis.opsForSet().remove(userSessionsKey(session.getUserId()), sessionId);
        redis.opsForSet().remove(instanceSessionsKey(session.getInstanceId()), sessionId);
        decrementSessionCountSafely();

        Long remaining = redis.opsForSet().size(userSessionsKey(session.getUserId()));
        if (remaining != null && remaining == 0L) {
            redis.opsForSet().remove(USERS_KEY, session.getUserId().toString());
        }

        log.info("[REGISTRY][REDIS] Unregistered session={} user={} instance={}",
                sessionId, session.getUserId(), session.getInstanceId());
    }

    @Override
    public Optional<RealtimeSession> findBySessionId(String sessionId) {
        Map<Object, Object> hash = redis.opsForHash().entries(sessionKey(sessionId));
        if (hash == null || hash.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(fromHash(hash, sessionId));
    }

    @Override
    public Collection<RealtimeSession> findByUserId(UUID userId) {
        Set<String> sessionIds = redis.opsForSet().members(userSessionsKey(userId));
        return loadSessions(sessionIds);
    }

    @Override
    public Collection<RealtimeSession> findByChannel(String channel) {
        Set<String> sessionIds = redis.opsForSet().members(subscriptionSessionsKey(channel));
        return loadSessions(sessionIds);
    }

    @Override
    public Collection<RealtimeSession> findByUserIdOwnedByCurrentInstance(UUID userId) {
        return findByUserId(userId).stream()
                .filter(s -> instanceId.equals(s.getInstanceId()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<RealtimeSession> findByChannelOwnedByCurrentInstance(String channel) {
        return findByChannel(channel).stream()
                .filter(s -> instanceId.equals(s.getInstanceId()))
                .collect(Collectors.toList());
    }

    @Override
    public void addSubscription(String sessionId, String channel) {
        String sessionSubscriptionsKey = sessionSubscriptionsKey(sessionId);
        String subscriptionSessionsKey = subscriptionSessionsKey(channel);

        redis.opsForSet().add(sessionSubscriptionsKey, channel);
        redis.opsForSet().add(subscriptionSessionsKey, sessionId);
        redis.expire(sessionSubscriptionsKey, leaseTtl);
        redis.expire(subscriptionSessionsKey, leaseTtl);
        refreshSessionLease(sessionId);
    }

    @Override
    public void removeSubscription(String sessionId, String channel) {
        redis.opsForSet().remove(sessionSubscriptionsKey(sessionId), channel);
        redis.opsForSet().remove(subscriptionSessionsKey(channel), sessionId);
        redis.expire(sessionSubscriptionsKey(sessionId), leaseTtl);
        redis.expire(subscriptionSessionsKey(channel), leaseTtl);
        refreshSessionLease(sessionId);
    }

    @Override
    public void refreshSessionLease(String sessionId) {
        String sessionKey = sessionKey(sessionId);
        if (!Boolean.TRUE.equals(redis.hasKey(sessionKey))) {
            return;
        }
        Instant now = Instant.now();
        redis.opsForHash().put(sessionKey, "lastActivityAt", now.toString());
        redis.opsForHash().put(sessionKey, "leaseExpiresAt", nextLeaseExpiry(now).toString());
        redis.expire(sessionKey, leaseTtl);

        Object userId = redis.opsForHash().get(sessionKey, "userId");
        if (userId != null) {
            redis.expire(USER_SESSIONS_PREFIX + userId, leaseTtl);
            redis.expire(USERS_KEY, leaseTtl);
        }

        Object ownerInstanceId = redis.opsForHash().get(sessionKey, "instanceId");
        if (ownerInstanceId != null) {
            redis.expire(INSTANCE_SESSIONS_PREFIX + ownerInstanceId, leaseTtl);
        }

        String sessionSubscriptionsKey = sessionSubscriptionsKey(sessionId);
        redis.expire(sessionSubscriptionsKey, leaseTtl);
        Set<String> channels = redis.opsForSet().members(sessionSubscriptionsKey);
        if (channels != null) {
            for (String channel : channels) {
                redis.expire(subscriptionSessionsKey(channel), leaseTtl);
            }
        }
    }

    @Override
    public int evictStaleSessions() {
        Instant now = Instant.now();
        int evicted = 0;
        int scanned = 0;

        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(SESSION_PREFIX + "*")
                .count(200)
                .build())) {
            while (cursor.hasNext() && scanned < cleanupBatchSize) {
                String key = cursor.next();
                scanned++;

                // The scan pattern "realtime:session:*" also matches non-hash keys
                // such as "realtime:session:count" (STRING) and
                // "realtime:session:subscriptions:*" (SET). Skip them to avoid WRONGTYPE.
                try {
                    String keyType = redis.type(key).code();
                    if (!"hash".equals(keyType)) {
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }

                String sessionId = key.substring(SESSION_PREFIX.length());
                Map<Object, Object> hash = redis.opsForHash().entries(key);
                if (hash == null || hash.isEmpty()) {
                    continue;
                }

                Instant leaseExpiresAt = parseInstant(getString(hash, "leaseExpiresAt"));
                if (!leaseExpiresAt.isBefore(now)) {
                    continue;
                }

                try {
                    unregister(sessionId);
                    evicted++;
                    log.info("[REGISTRY][REDIS][STALE-EVICT] sessionId={} leaseExpiresAt={} now={}",
                            sessionId, leaseExpiresAt, now);
                } catch (Exception ex) {
                    log.warn("[REGISTRY][REDIS][STALE-EVICT] Failed to evict stale sessionId={}", sessionId, ex);
                }
            }
        } catch (Exception ex) {
            log.warn("[REGISTRY][REDIS][STALE-EVICT] Scan failed", ex);
        }
        return evicted;
    }

    @Override
    public int cleanupOrphanIndexes() {
        int cleaned = 0;

        cleaned += cleanupSessionIdSetMembers(USER_SESSIONS_PREFIX + "*");
        cleaned += cleanupSessionIdSetMembers(SUBSCRIPTION_SESSIONS_PREFIX + "*");
        cleaned += cleanupSessionIdSetMembers(INSTANCE_SESSIONS_PREFIX + "*");

        Set<String> userIds = redis.opsForSet().members(USERS_KEY);
        if (userIds != null) {
            for (String userId : userIds) {
                Long size = redis.opsForSet().size(USER_SESSIONS_PREFIX + userId);
                if (size != null && size == 0L) {
                    redis.opsForSet().remove(USERS_KEY, userId);
                    cleaned++;
                }
            }
        }

        if (cleaned > 0) {
            log.info("[REGISTRY][REDIS][ORPHAN-CLEANUP] removedEntries={}", cleaned);
        }
        return cleaned;
    }

    @Override
    public int getActiveSessionCount() {
        String raw = redis.opsForValue().get(SESSION_COUNT_KEY);
        if (raw == null || raw.isBlank()) {
            return 0;
        }

        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            log.warn("[REGISTRY][REDIS] Invalid session counter value={}", raw, ex);
            return 0;
        }
    }

    @Override
    public int getActiveLocalSessionCount() {
        Long size = redis.opsForSet().size(instanceSessionsKey(instanceId));
        return size == null ? 0 : size.intValue();
    }

    @Override
    public int getActiveUserCount() {
        Long size = redis.opsForSet().size(USERS_KEY);
        return size == null ? 0 : size.intValue();
    }

    @Override
    public String getCurrentInstanceId() {
        return instanceId;
    }

    private Collection<RealtimeSession> loadSessions(Set<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }

        List<RealtimeSession> sessions = new ArrayList<>();
        for (String sessionId : sessionIds) {
            Optional<RealtimeSession> session = findBySessionId(sessionId);
            session.ifPresent(sessions::add);
        }
        return sessions;
    }

    private RealtimeSession fromHash(Map<Object, Object> hash, String sessionId) {
        UUID userId = UUID.fromString(getString(hash, "userId"));
        String owner = getString(hash, "instanceId");
        Instant connectedAt = parseInstant(getString(hash, "connectedAt"));
        Instant lastActivityAt = parseInstant(getString(hash, "lastActivityAt"));
        Instant leaseExpiresAt = parseInstant(getString(hash, "leaseExpiresAt"));
        String stateRaw = getString(hash, "state");
        RealtimeSession.ConnectionState state = parseState(stateRaw);

        Set<String> subscriptions = redis.opsForSet().members(sessionSubscriptionsKey(sessionId));
        if (subscriptions == null) {
            subscriptions = Set.of();
        }

        return RealtimeSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .instanceId(owner)
                .connectedAt(connectedAt)
                .lastActivityAt(lastActivityAt)
                .leaseExpiresAt(leaseExpiresAt)
                .state(state)
                .subscriptions(new HashSet<>(subscriptions))
                .build();
    }

    private int cleanupSessionIdSetMembers(String keyPattern) {
        int cleaned = 0;
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(keyPattern)
                .count(200)
                .build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Set<String> members = redis.opsForSet().members(key);
                if (members == null || members.isEmpty()) {
                    continue;
                }
                for (String sessionId : members) {
                    if (!Boolean.TRUE.equals(redis.hasKey(sessionKey(sessionId)))) {
                        redis.opsForSet().remove(key, sessionId);
                        cleaned++;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[REGISTRY][REDIS][ORPHAN-CLEANUP] Scan failed keyPattern={}", keyPattern, ex);
        }
        return cleaned;
    }

    private Instant nextLeaseExpiry() {
        return nextLeaseExpiry(Instant.now());
    }

    private Instant nextLeaseExpiry(Instant from) {
        return from.plus(leaseTtl);
    }

    private String getString(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        return value == null ? null : value.toString();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }

    private RealtimeSession.ConnectionState parseState(String value) {
        if (value == null || value.isBlank()) {
            return RealtimeSession.ConnectionState.CONNECTED;
        }
        try {
            return RealtimeSession.ConnectionState.valueOf(value);
        } catch (Exception ex) {
            return RealtimeSession.ConnectionState.CONNECTED;
        }
    }

    private String sessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String userSessionsKey(UUID userId) {
        return USER_SESSIONS_PREFIX + userId;
    }

    private String subscriptionSessionsKey(String subscription) {
        return SUBSCRIPTION_SESSIONS_PREFIX + subscription;
    }

    private String sessionSubscriptionsKey(String sessionId) {
        return SESSION_SUBSCRIPTIONS_PREFIX + sessionId;
    }

    private String instanceSessionsKey(String ownerInstanceId) {
        return INSTANCE_SESSIONS_PREFIX + ownerInstanceId;
    }

    private void decrementSessionCountSafely() {
        Long count = redis.opsForValue().decrement(SESSION_COUNT_KEY);
        if (count == null || count < 0) {
            redis.opsForValue().set(SESSION_COUNT_KEY, "0");
        }
    }
}
