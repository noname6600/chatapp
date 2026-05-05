package com.example.common.redis.cache.api;

import com.example.common.redis.cache.core.TimeRedisCache;
import com.example.common.redis.cache.exception.CreateCacheException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;

public interface ITimeRedisCacheManager extends CacheManager {
    default <F> F get(String cacheName, Object key, Class<F> responseClass) throws CreateCacheException {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            throw new CreateCacheException("Cannot get cache " + cacheName);
        }
        if (isCacheUnAvailable()) {
            clearCacheFail();
        }
        try {
            return cache.get(key, responseClass);
        } catch (RedisConnectionFailureException exception) {
            cacheUnAvailable(cacheName);
            return null;
        }
    }

    default void put(String cacheName, Object key, Object value, Duration ttl) throws CreateCacheException {
        TimeRedisCache cache = (TimeRedisCache) getCache(cacheName);
        if (cache == null) {
            throw new CreateCacheException("Cannot get cache " + cacheName);
        }
        if (isCacheUnAvailable()) {
            clearCacheFail();
        }
        try {
            cache.put(key, value, ttl);
        } catch (RedisConnectionFailureException exception) {
            cacheUnAvailable(cacheName);
        }
    }

    default void evict(String cacheName, Object key) throws CreateCacheException {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            throw new CreateCacheException("Cannot get cache " + cacheName);
        }
        if (isCacheUnAvailable()) {
            clearCacheFail();
        }
        try {
            cache.evict(key);
        } catch (RedisConnectionFailureException exception) {
            cacheUnAvailable(cacheName);
        }
    }

    default void clear(String cacheName) throws CreateCacheException {
        Cache cache = getCache(cacheName);
        if (cache == null) {
            throw new CreateCacheException("Cannot get cache " + cacheName);
        }
        if (isCacheUnAvailable()) {
            clearCacheFail();
        }
        try {
            cache.clear();
        } catch (RedisConnectionFailureException exception) {
            cacheUnAvailable(cacheName);
        }
    }

    void cacheUnAvailable(String cacheName);

    boolean isCacheUnAvailable();

    void clearCacheFail();
}
