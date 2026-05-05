package com.example.common.redis.core;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

/**
 * Backward-compatible alias for cache manager implementation.
 */
@Deprecated(forRemoval = false, since = "2.1")
public class TimeRedisCacheManager extends com.example.common.redis.cache.core.TimeRedisCacheManager {

    public TimeRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, String serviceName) {
        super(cacheWriter, defaultCacheConfiguration, serviceName);
    }

    public TimeRedisCacheManager(
            RedisCacheWriter cacheWriter,
            RedisCacheConfiguration defaultCacheConfiguration,
            boolean allowInFlightCacheCreation,
            String serviceName
    ) {
        super(cacheWriter, defaultCacheConfiguration, allowInFlightCacheCreation, serviceName);
    }
}
