package com.chatweb.realtime.config;

import com.chatweb.common.redis.registry.RedisEventRegistry;
import com.chatweb.realtime.dispatch.EdgeDeliveryHandoffEvent;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class RealtimeRedisEventRegistryConfigurer {

    private final RedisEventRegistry redisEventRegistry;

    public RealtimeRedisEventRegistryConfigurer(RedisEventRegistry redisEventRegistry) {
        this.redisEventRegistry = redisEventRegistry;
    }

    @PostConstruct
    public void registerRealtimeEdgeRedisEvents() {
        if (!redisEventRegistry.contains(EdgeDeliveryHandoffEvent.REDIS_EVENT_TYPE)) {
            redisEventRegistry.register(EdgeDeliveryHandoffEvent.REDIS_EVENT_TYPE, EdgeDeliveryHandoffEvent.class);
        }
    }
}
