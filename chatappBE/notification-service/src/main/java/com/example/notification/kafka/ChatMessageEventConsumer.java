package com.example.notification.kafka;

import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.kafka.event.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageEventConsumer {

    @KafkaListener(topics = KafkaTopics.CHAT_MESSAGE_SENT)
    public void listen(ChatMessageSentEvent event) {
        // MESSAGE fanout is handled by MessageCreatedEventConsumer with per-recipient mode checks.
        log.debug("[NOTI] Ignoring legacy ChatMessageEventConsumer path for event {}", event == null ? null : event.getEventId());
    }
}

