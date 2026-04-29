package com.example.chat.modules.message.infrastructure.kafka;

import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.common.integration.kafka.KafkaTopics;
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

        @KafkaListener(
            topics = KafkaTopics.CHAT_REACTION_UPDATED,
            groupId = "chat-service-realtime-fanout",
            properties = {"auto.offset.reset=latest"}
        )
    public void onReactionUpdated(
            ChatReactionUpdatedEvent event
    ) {

        chatRedisPublisher.publishReactionUpdated(
                event.getPayload()
        );
    }
}
