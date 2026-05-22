package com.chatweb.common.redis.registry;

import com.chatweb.common.event.DefaultEventPayloadRegistry;

/**
 * Default Redis event registry.
 *
 * <p>Extends {@link DefaultEventPayloadRegistry} with the {@link RedisEventRegistry}
 * marker interface so that service modules can inject the registry by the Redis-specific
 * type. Pre-populated by {@link com.chatweb.common.redis.config.RedisAutoConfiguration}
 * via {@link com.chatweb.common.event.SharedEventCatalog#registerAll}.
 */
public class DefaultRedisEventRegistry extends DefaultEventPayloadRegistry implements RedisEventRegistry {
}
