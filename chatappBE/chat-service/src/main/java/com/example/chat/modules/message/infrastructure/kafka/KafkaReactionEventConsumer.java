package com.example.chat.modules.message.infrastructure.kafka;

import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.chat.realtime.subscriber.RealtimeEventDedupeGuard;
import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.kafka.event.ChatReactionUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaReactionEventConsumer {

    private final ChatRedisPublisher chatRedisPublisher;
    private final RealtimeEventDedupeGuard dedupeGuard;

        @KafkaListener(
            topics = KafkaTopics.CHAT_REACTION_UPDATED,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onReactionUpdated(
            ChatReactionUpdatedEvent event
    ) {
        if (event == null || event.getPayload() == null) {
            return;
        }
        if (dedupeGuard.isDuplicate(event.getEventId())) {
            log.info("[realtime-fanout] duplicate kafka reaction-updated dropped eventId={}", event.getEventId());
            return;
        }

        chatRedisPublisher.publishReactionUpdated(
            event.getPayload(),
            event.getEventId() == null ? null : event.getEventId().toString(),
            event.getCorrelationId()
        );
    }
}
