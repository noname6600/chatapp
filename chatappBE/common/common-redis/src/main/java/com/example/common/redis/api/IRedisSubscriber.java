package com.example.common.redis.api;

public interface IRedisSubscriber<T extends IRedisMessage> {

    String eventType();

    void onMessage(T message);
}

