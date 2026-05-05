package com.example.common.redis.exception;

/**
 * Backward-compatible alias for cache exception.
 */
@Deprecated(forRemoval = false, since = "2.1")
public class CreateCacheException extends com.example.common.redis.cache.exception.CreateCacheException {
    public CreateCacheException(String message) {
        super(message);
    }
}
