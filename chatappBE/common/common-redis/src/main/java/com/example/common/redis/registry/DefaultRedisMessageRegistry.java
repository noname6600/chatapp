package com.example.common.redis.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class DefaultRedisMessageRegistry
        implements IRedisMessageRegistry {

    private final Map<String, Class<?>> registry =
            new ConcurrentHashMap<>();

    @Override
    public void register(String eventType, Class<?> payloadClass) {

        if (registry.containsKey(eventType)) {
            throw new IllegalStateException(
                    "Duplicate Redis eventType=" + eventType
            );
        }

        registry.put(eventType, payloadClass);
    }

    @Override
    public Class<?> resolvePayload(String eventType) {

        Class<?> clazz = registry.get(eventType);

        if (clazz == null) {
            throw new IllegalStateException(
                    "Unknown Redis eventType=" + eventType
            );
        }

        return clazz;
    }
}