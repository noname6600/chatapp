package com.example.common.redis.registry;

import com.example.common.event.EventPayloadRegistry;

/**
 * Redis-scoped adapter of the transport-independent {@link EventPayloadRegistry}.
 *
 * <p>Services that need to register custom (non-shared) event types for Redis
 * deserialization inject this interface. All shared event types are pre-populated
 * by {@link com.example.common.redis.config.RedisAutoConfiguration} via
 * {@link com.example.common.event.SharedEventCatalog#registerAll}.
 *
 * <p>This interface adds no Redis-specific behavior beyond the common registry contract.
 * It is kept as a service-facing adapter to preserve injection-by-type compatibility.
 */
public interface RedisEventRegistry extends EventPayloadRegistry {
}
