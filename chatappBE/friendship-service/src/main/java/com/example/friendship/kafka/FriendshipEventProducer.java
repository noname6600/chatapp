package com.example.friendship.kafka;

import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.integration.friendship.FriendshipPayload;
import com.example.common.kafka.Topics;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.kafka.event.FriendRequestKafkaEvent;
import com.example.common.kafka.event.FriendshipEvent;
import com.example.friendship.entity.Friendship;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FriendshipEventProducer {

    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    public void publish(FriendshipEventType type, Friendship friendship) {

        FriendshipPayload payload = new FriendshipPayload(
                friendship.getUserLow(),
                friendship.getUserHigh(),
                friendship.getActionUserId(),
                friendship.getStatus().name()
        );

        kafkaEventPublisher.publish(
                Topics.FRIENDSHIP_EVENTS,
                friendship.getId().toString(),
                FriendshipEvent.of(sourceService, type, payload)
        );
    }

            public void publishFriendRequestEvent(
                UUID senderId,
                UUID recipientId,
                UUID requestId,
                FriendRequestEvent.Type type
            ) {

            FriendRequestEvent payload = FriendRequestEvent.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .requestId(requestId)
                .type(type)
                .build();

            kafkaEventPublisher.publish(
                Topics.FRIENDSHIP_REQUEST_EVENTS,
                requestId.toString(),
                FriendRequestKafkaEvent.of(sourceService, payload)
            );
            }
}


