package com.example.common.redis.api;

import java.time.Instant;
import java.util.UUID;

public interface IRedisMessage {

    String getMessageId();

    String getEventType();

    String getSourceService();

    Instant getCreatedAt();
}



