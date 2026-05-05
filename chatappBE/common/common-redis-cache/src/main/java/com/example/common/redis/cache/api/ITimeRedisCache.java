package com.example.common.redis.cache.api;

import org.springframework.cache.Cache;
import org.springframework.lang.Nullable;

import java.time.Duration;

public interface ITimeRedisCache extends Cache {

    void put(Object key, @Nullable Object value, Duration ttl);

    ValueWrapper putIfAbsent(Object key, @Nullable Object value, Duration ttl);
}
