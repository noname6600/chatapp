package com.chatweb.chat.modules.message.infrastructure.redis;

import com.chatweb.chat.modules.message.application.service.IReactionEventPublisher;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReactionEventPublisherAdapter implements IReactionEventPublisher {

    private final ChatRedisPublisher chatRedisPublisher;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    @Override
    public void publishReactionUpdated(ReactionPayload payload) {
        chatRedisPublisher.publishRoomRealtimeEvent(
                payload.getRoomId(), ChatEventType.REACTION_UPDATED.value(), payload
        );
        String eventId = UUID.randomUUID().toString();
        kafkaEventPublisher.publish(
                ChatEventType.REACTION_UPDATED.value(),
                payload.getRoomId().toString(),
                new EventEnvelope<>(
                        new EventMetadata(eventId, ChatEventType.REACTION_UPDATED.value(), sourceService, Instant.now(), TraceContext.correlationIdOrEventId(eventId)),
                        payload
                )
        );
    }
}
