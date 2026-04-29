package com.example.friendship.kafka;

import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.integration.friendship.FriendshipPayload;
import com.example.common.integration.kafka.KafkaTopics;
import com.example.common.kafka.api.KafkaEventPublisher;
import com.example.common.integration.kafka.event.FriendRequestKafkaEvent;
import com.example.common.integration.kafka.event.FriendshipEvent;
import com.example.common.web.response.ApiResponse;
import com.example.friendship.client.UserClient;
import com.example.friendship.dto.UserProfileResponse;
import com.example.friendship.entity.Friendship;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FriendshipEventProducer {

    private final KafkaEventPublisher kafkaEventPublisher;
    private final UserClient userClient;

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
                KafkaTopics.FRIENDSHIP_EVENTS,
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

            String senderDisplayName = resolveSenderDisplayName(senderId);

            FriendRequestEvent payload = FriendRequestEvent.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .requestId(requestId)
                .senderDisplayName(senderDisplayName)
                .createdAt(Instant.now())
                .type(type)
                .build();

            kafkaEventPublisher.publish(
                KafkaTopics.FRIENDSHIP_REQUEST_EVENTS,
                requestId.toString(),
                FriendRequestKafkaEvent.of(sourceService, payload)
            );
            }

    private String resolveSenderDisplayName(UUID senderId) {
        if (senderId == null) return null;

        try {
            ApiResponse<List<UserProfileResponse>> response = userClient.getUsersBulk(List.of(senderId));
            List<UserProfileResponse> rows = response == null ? null : response.getData();
            if (rows == null || rows.isEmpty()) {
                return senderId.toString();
            }

            UserProfileResponse me = rows.stream()
                    .filter(u -> u != null && senderId.equals(u.getAccountId()))
                    .findFirst()
                    .orElse(null);

            if (me == null) return senderId.toString();
            if (me.getDisplayName() != null && !me.getDisplayName().isBlank()) return me.getDisplayName();
            if (me.getUsername() != null && !me.getUsername().isBlank()) return me.getUsername();
            return senderId.toString();
        } catch (Exception ignored) {
            return senderId.toString();
        }
    }
}


