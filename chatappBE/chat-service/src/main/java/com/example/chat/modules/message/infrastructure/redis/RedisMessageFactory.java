package com.example.chat.modules.message.infrastructure.redis;

import com.example.common.redis.message.RedisMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisMessageFactory {

    @Value("${spring.application.name}")
    private String sourceService;

    public <T> RedisMessage<T> create(String eventType, T payload) {

        return RedisMessage.<T>builder()
                .messageId(UUID.randomUUID().toString())
                .eventType(eventType)
                .sourceService(sourceService)
                .createdAt(Instant.now())
                .payload(payload)
                .build();
    }
}
