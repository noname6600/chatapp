package com.example.chat.modules.message.infrastructure.kafka;

import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.chat.realtime.subscriber.RealtimeEventDedupeGuard;
import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.kafka.event.ChatMessageDeletedEvent;
import com.example.common.integration.kafka.event.ChatMessageEditedEvent;
import com.example.common.integration.kafka.event.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaChatMessageEventConsumer {

    private final ChatRedisPublisher chatRedisPublisher;
    private final RealtimeEventDedupeGuard dedupeGuard;

        @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGE_SENT,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onMessageSent(ChatMessageSentEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }
        if (dedupeGuard.isDuplicate(event.getEventId())) {
            log.info("[realtime-fanout] duplicate kafka message-sent dropped eventId={}", event.getEventId());
            return;
        }

        chatRedisPublisher.publishMessageSent(
            event.getPayload(),
            event.getEventId() == null ? null : event.getEventId().toString(),
            event.getCorrelationId()
        );
    }

        @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGE_EDITED,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onMessageEdited(ChatMessageEditedEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }
        if (dedupeGuard.isDuplicate(event.getEventId())) {
            log.info("[realtime-fanout] duplicate kafka message-edited dropped eventId={}", event.getEventId());
            return;
        }

        chatRedisPublisher.publishMessageEdited(
            event.getPayload(),
            event.getEventId() == null ? null : event.getEventId().toString(),
            event.getCorrelationId()
        );
    }

        @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGE_DELETED,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onMessageDeleted(ChatMessageDeletedEvent event) {
        if (event == null || event.getPayload() == null) {
            return;
        }
        if (dedupeGuard.isDuplicate(event.getEventId())) {
            log.info("[realtime-fanout] duplicate kafka message-deleted dropped eventId={}", event.getEventId());
            return;
        }

        chatRedisPublisher.publishMessageDeleted(
            event.getPayload(),
            event.getEventId() == null ? null : event.getEventId().toString(),
            event.getCorrelationId()
        );
    }
}