package com.example.common.redis.registry;


import com.example.common.redis.api.IRedisMessage;

public interface IRedisMessageRegistry {

    void register(String eventType, Class<?> payloadClass);

    Class<?> resolvePayload(String eventType);

}



