package com.example.common.redis.message;

import com.example.common.redis.api.IRedisMessage;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
public abstract class AbstractRedisMessage implements IRedisMessage {

    protected final String messageId;
    protected final String eventType;
    protected final String sourceService;
    protected final Instant createdAt;

    protected static String newMessageId() {
        return UUID.randomUUID().toString();
    }

    protected static Instant now() {
        return Instant.now();
    }
}
