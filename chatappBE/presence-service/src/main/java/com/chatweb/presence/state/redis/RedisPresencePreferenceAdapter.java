package com.chatweb.presence.state.redis;

import com.chatweb.common.redis.cache.api.ITimeRedisCacheManager;
import com.chatweb.common.redis.cache.exception.CreateCacheException;
import com.chatweb.presence.service.model.StoredPresenceState;
import com.chatweb.presence.state.port.PresencePreferencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisPresencePreferenceAdapter implements PresencePreferencePort {

    private static final String CACHE = "presence-pref";
    private static final Duration PREFERENCE_TTL = Duration.ofDays(7);
    private static final String USER_PREFIX = "presence::preference::user:";

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
    public void save(UUID userId, StoredPresenceState preference) {
        try {
            cacheManager.put(CACHE, userKey(userId), preference, PREFERENCE_TTL);
        } catch (CreateCacheException ignored) {
        }
    }

    private String userKey(UUID userId) {
        return USER_PREFIX + userId;
    }
}
