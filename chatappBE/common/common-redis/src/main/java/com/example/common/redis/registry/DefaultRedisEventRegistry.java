package com.example.common.redis.registry;

import com.example.common.event.DefaultEventPayloadRegistry;

/**
 * Default Redis event registry.
 *
 * <p>Extends {@link DefaultEventPayloadRegistry} with the {@link RedisEventRegistry}
 * marker interface so that service modules can inject the registry by the Redis-specific
 * type. Pre-populated by {@link com.example.common.redis.config.RedisAutoConfiguration}
 * via {@link com.example.common.event.SharedEventCatalog#registerAll}.
 */
public class DefaultRedisEventRegistry extends DefaultEventPayloadRegistry implements RedisEventRegistry {
}
