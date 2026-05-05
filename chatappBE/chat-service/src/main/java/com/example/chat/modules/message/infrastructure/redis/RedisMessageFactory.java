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
        String messageId = UUID.randomUUID().toString();

        return RedisMessage.<T>builder()
            .messageId(messageId)
            .eventId(messageId)
            .correlationId(messageId)
            .eventType(eventType)
            .sourceService(sourceService)
            .createdAt(Instant.now())
            .payload(payload)
            .build();
        }

        public <T> RedisMessage<T> create(
            String eventType,
            T payload,
            String eventId,
            String correlationId
        ) {
        String messageId = UUID.randomUUID().toString();

        return RedisMessage.<T>builder()
            .messageId(messageId)
            .eventId(eventId != null ? eventId : messageId)
            .correlationId(correlationId != null ? correlationId : eventId != null ? eventId : messageId)
                .eventType(eventType)
                .sourceService(sourceService)
                .createdAt(Instant.now())
                .payload(payload)
                .build();
    }
}
