package com.example.common.redis.publisher;

import com.example.common.redis.api.IRedisMessage;
import com.example.common.redis.api.IRedisPublisher;
import com.example.common.redis.exception.RedisPubSubException;
import com.example.common.redis.observability.IRedisPubSubLogger;
import com.example.common.redis.serialization.IRedisMessageSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public class DefaultRedisPublisher
        implements IRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final IRedisMessageSerializer serializer;
    private final IRedisPubSubLogger logger;

    @Override
    public void publish(String channel, IRedisMessage message) {
        try {
            String payload = serializer.serialize(message);
            redisTemplate.convertAndSend(channel, payload);
            logger.logPublish(channel, message);
        } catch (Exception ex) {
            logger.logError(channel, message, ex);
            throw new RedisPubSubException(
                    channel,
                    "Failed to publish RedisMessage",
                    ex
            );
        }
    }
}



