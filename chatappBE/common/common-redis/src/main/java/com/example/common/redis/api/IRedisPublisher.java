package com.example.common.redis.api;


public interface IRedisPublisher {
    void publish(String channel, IRedisMessage message);
}

