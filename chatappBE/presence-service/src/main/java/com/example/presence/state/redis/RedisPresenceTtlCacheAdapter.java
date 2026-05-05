package com.example.presence.state.redis;

import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.redis.exception.CreateCacheException;
import com.example.presence.service.model.StoredPresenceState;
import com.example.presence.state.port.PresenceTtlCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisPresenceTtlCacheAdapter implements PresenceTtlCachePort {

    private static final String CACHE = "presence";
    private static final Duration USER_TTL = Duration.ofSeconds(30);
    private static final String USER_PREFIX = "presence::user:";

    private final ITimeRedisCacheManager cacheManager;

    @Override
    public StoredPresenceState get(UUID userId) {
        try {
            return cacheManager.get(CACHE, userKey(userId), StoredPresenceState.class);
        } catch (CreateCacheException e) {
            return null;
        }
    }

    @Override
    public void put(UUID userId, StoredPresenceState state) {
        try {
            cacheManager.put(CACHE, userKey(userId), state, USER_TTL);
        } catch (CreateCacheException ignored) {
        }
    }

    @Override
    public void evict(UUID userId) {
        try {
            cacheManager.evict(CACHE, userKey(userId));
        } catch (CreateCacheException ignored) {
        }
    }

    private String userKey(UUID userId) {
        return USER_PREFIX + userId;
    }
}
