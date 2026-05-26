package com.chatweb.chat.modules.message.infrastructure.redis;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.redis.channel.RedisChannels;
import com.chatweb.common.redis.publisher.RedisEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatRedisPublisher {

    private final RedisEventPublisher redisPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    public <T> void publishRoomRealtimeEvent(UUID roomId, String eventType, T payload) {
        String eventId = UUID.randomUUID().toString();
        redisPublisher.publish(
                RedisChannels.chatRoom(roomId),
                new EventEnvelope<>(
                        new EventMetadata(eventId, eventType, sourceService, Instant.now(), TraceContext.correlationIdOrEventId(eventId)),
                        payload
                )
        );
    }
}
