package com.example.common.redis.observability;


import com.example.common.redis.api.IRedisMessage;

public interface IRedisPubSubLogger {

    void logPublish(String channel, IRedisMessage message);

    void logReceive(String channel, IRedisMessage message);

    void logForward(String destination, IRedisMessage message);

    void logError(String channel, IRedisMessage message, Throwable ex);

    void logDeserializeError(String channel, String rawPayload, Exception ex);
}
