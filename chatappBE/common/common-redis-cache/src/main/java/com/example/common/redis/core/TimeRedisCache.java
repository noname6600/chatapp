package com.example.common.redis.core;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;

/**
 * Backward-compatible alias for cache implementation.
 */
@Deprecated(forRemoval = false, since = "2.1")
public class TimeRedisCache extends com.example.common.redis.cache.core.TimeRedisCache {

    public TimeRedisCache(String name, RedisCacheWriter cacheWriter, RedisCacheConfiguration cacheConfig, String serviceName) {
        super(name, cacheWriter, cacheConfig, serviceName);
    }
}
