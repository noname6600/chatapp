package com.example.chat.modules.message.infrastructure.kafka;

import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.common.kafka.Topics;
import com.example.common.kafka.event.ChatMessageDeletedEvent;
import com.example.common.kafka.event.ChatMessageEditedEvent;
import com.example.common.kafka.event.ChatMessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaChatMessageEventConsumer {

    private final ChatRedisPublisher chatRedisPublisher;

        @KafkaListener(
            topics = Topics.CHAT_MESSAGE_SENT,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onMessageSent(ChatMessageSentEvent event) {

        chatRedisPublisher.publishMessageSent(
                event.getPayload()
        );
    }

        @KafkaListener(
            topics = Topics.CHAT_MESSAGE_EDITED,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onMessageEdited(ChatMessageEditedEvent event) {

        chatRedisPublisher.publishMessageEdited(
                event.getPayload()
        );
    }

        @KafkaListener(
            topics = Topics.CHAT_MESSAGE_DELETED,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onMessageDeleted(ChatMessageDeletedEvent event) {

        chatRedisPublisher.publishMessageDeleted(
                event.getPayload()
        );
    }
}