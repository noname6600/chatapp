package com.example.common.redis.observability;

import com.example.common.redis.api.IRedisMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisPubSubLogger implements IRedisPubSubLogger {

    @Override
    public void logPublish(String channel, IRedisMessage message) {
        log.info(
                "Publishing RedisMessage channel={} eventType={} messageId={} sourceService={}",
                channel,
                message.getEventType(),
                message.getMessageId(),
                message.getSourceService()
        );
    }

    @Override
    public void logReceive(String channel, IRedisMessage message) {
        log.info(
                "Received RedisMessage channel={} eventType={} messageId={} sourceService={}",
                channel,
                message.getEventType(),
                message.getMessageId(),
                message.getSourceService()
        );
    }

    @Override
    public void logForward(String destination, IRedisMessage message) {
        log.info(
                "Forwarding RedisMessage to WS destination={} eventType={} messageId={}",
                destination,
                message.getEventType(),
                message.getMessageId()
        );
    }

    @Override
    public void logError(
            String channel,
            IRedisMessage message,
            Throwable ex
    ) {
        log.error(
                "Redis Pub/Sub error channel={} eventType={} messageId={}",
                channel,
                message != null ? message.getEventType() : "null",
                message != null ? message.getMessageId() : "null",
                ex
        );
    }

    @Override
    public void logDeserializeError(
            String channel,
            String rawPayload,
            Exception ex
    ) {
        log.error(
                "[REDIS][DESERIALIZE_FAIL] channel={} payload={}",
                channel,
                rawPayload,
                ex
        );
    }
}

