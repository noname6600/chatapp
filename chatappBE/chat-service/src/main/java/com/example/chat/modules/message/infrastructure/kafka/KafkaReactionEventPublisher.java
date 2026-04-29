package com.example.chat.modules.message.infrastructure.kafka;

import com.example.chat.modules.message.application.service.IReactionEventPublisher;
import com.example.common.integration.chat.ReactionPayload;
import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.integration.kafka.event.ChatReactionUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaReactionEventPublisher
        implements IReactionEventPublisher {

    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    @Override
    public void publishReactionUpdated(
            ReactionPayload payload
    ) {

        ChatReactionUpdatedEvent event =
                ChatReactionUpdatedEvent.from(
                        sourceService,
                        payload
                );

        kafkaEventPublisher.publish(
                KafkaTopics.CHAT_REACTION_UPDATED,
                payload.getRoomId().toString(),
                event
        );
    }
}