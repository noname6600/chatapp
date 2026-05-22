package com.chatweb.friendship.infrastructure.kafka;

import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.common.integration.friendship.FriendshipPayload;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.event.EventMetadata;
import com.chatweb.common.event.TraceContext;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.friendship.entity.Friendship;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FriendshipEventProducer {

    private final KafkaEventPublisher kafkaEventPublisher;

    @Value("${spring.application.name}")
    private String sourceService;

    /**
     * Publishes friendship events with correct payload type based on event classification.
     *
        * <p>Active topic contract:
        * - friend.request.* events -> {@link KafkaTopics#TOPIC_FRIENDSHIP_REQUEST_EVENTS}
        * - friendship status events -> {@link KafkaTopics#TOPIC_FRIENDSHIP_EVENTS}
        *
        * <p>Event semantic type is preserved in metadata.eventType.
        *
        * <p>Payload contract (per SharedEventCatalog):
     * - friend.request.* events: use FriendRequestPayload
     * - friend.* status change events: use FriendshipPayload
     */
    public void publish(FriendshipEventType type, Friendship friendship) {

        String eventId = UUID.randomUUID().toString();
        EventMetadata metadata = new EventMetadata(
            eventId,
            type.value(),
            sourceService,
            Instant.now(),
            TraceContext.correlationIdOrEventId(eventId)
        );

        // Determine payload type based on event classification
        if (isFriendRequestEvent(type)) {
            // Friend request lifecycle events use FriendRequestPayload
            FriendRequestPayload payload = buildFriendRequestPayload(friendship);
            EventEnvelope<FriendRequestPayload> envelope = new EventEnvelope<>(metadata, payload);
            kafkaEventPublisher.publish(KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS, friendship.getId().toString(), envelope);
        } else {
            // Status change events (UNFRIENDED, BLOCKED, UNBLOCKED) use FriendshipPayload
            FriendshipPayload payload = new FriendshipPayload(
                    friendship.getUserLow(),
                    friendship.getUserHigh(),
                    friendship.getActionUserId(),
                    friendship.getStatus().name()
            );
            EventEnvelope<FriendshipPayload> envelope = new EventEnvelope<>(metadata, payload);
            kafkaEventPublisher.publish(KafkaTopics.TOPIC_FRIENDSHIP_EVENTS, friendship.getId().toString(), envelope);
        }
    }

    /**
     * Detects if event type is part of friend request lifecycle.
     */
    private boolean isFriendRequestEvent(FriendshipEventType type) {
        return type == FriendshipEventType.FRIEND_REQUEST_SENT
                || type == FriendshipEventType.FRIEND_REQUEST_ACCEPTED
                || type == FriendshipEventType.FRIEND_REQUEST_DECLINED
                || type == FriendshipEventType.FRIEND_REQUEST_CANCELLED;
    }

    /**
     * Builds FriendRequestPayload from Friendship entity for friend.request.* events.
     * Uses ID-first sender labeling to avoid synchronous downstream dependencies.
     */
    private FriendRequestPayload buildFriendRequestPayload(Friendship friendship) {
        // actionUserId is the sender (who initiated the request)
        UUID senderId = friendship.getActionUserId();
        // recipient is the other party
        UUID recipientId = friendship.getUserLow().equals(senderId) ? friendship.getUserHigh() : friendship.getUserLow();
        String senderDisplayName = senderId == null ? null : senderId.toString();

        return FriendRequestPayload.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .requestId(friendship.getId())
                .senderDisplayName(senderDisplayName)
                .createdAt(friendship.getCreatedAt())
                .build();
    }
}


