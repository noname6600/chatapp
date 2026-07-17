package com.chatweb.realtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RedisListenerHealthIndicator implements HealthIndicator {

    @Value("${realtime.redis.listener.enabled:false}")
    private boolean redisListenerEnabled;

    @Override
    public Health health() {
        if (redisListenerEnabled) {
            return Health.up().withDetail("realtimeRedisListenerEnabled", true).build();
        }
        return Health.down()
                .withDetail("realtimeRedisListenerEnabled", false)
                .withDetail("reason", "realtime.redis.listener.enabled=false")
                .build();
    }
}
