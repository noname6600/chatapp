package com.example.friendship.kafka;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendshipPayload;
import com.example.common.integration.kafka.event.FriendRequestKafkaEvent;
import com.example.common.integration.kafka.event.FriendshipEvent;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.friendship.realtime.port.FriendshipRealtimePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipRealtimeConsumerTest {

    @Mock
    private FriendshipRealtimePort friendshipRealtimePort;

    @Mock
    private FriendshipEventDedupeGuard dedupeGuard;

    @InjectMocks
    private FriendshipEventConsumer friendshipEventConsumer;

    @InjectMocks
    private FriendshipRequestEventConsumer friendshipRequestEventConsumer;

    @BeforeEach
    void setUp() {
        when(dedupeGuard.isDuplicate(org.mockito.ArgumentMatchers.any())).thenReturn(false);
    }

    @Test
    void friendshipKafkaEvent_dispatchesNormalizedTypeAndFlowId() {
        UUID userLow = UUID.randomUUID();
        UUID userHigh = UUID.randomUUID();
        FriendshipPayload payload = new FriendshipPayload(userLow, userHigh, userLow, "FRIENDS");

        FriendshipEvent event = FriendshipEvent.of(
                "friendship-service",
                FriendshipEventType.FRIEND_REQUEST_ACCEPTED,
                payload
        );

        friendshipEventConsumer.listen(event);

        verify(friendshipRealtimePort).publishRelationshipEvent(
                eq(userLow),
                eq(userHigh),
                eq(FriendshipEventType.FRIEND_REQUEST_ACCEPTED.value()),
                eq(payload),
                eq(RealtimeFlowId.FRIENDSHIP_REQUEST_ACCEPTED)
        );
    }

    @Test
    void friendRequestKafkaEvent_dispatchesAcceptedFlowWithRequestPayload() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        FriendRequestEvent payload = FriendRequestEvent.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .requestId(UUID.randomUUID())
                .createdAt(Instant.now())
                .type(FriendRequestEvent.Type.ACCEPTED)
                .build();

        FriendRequestKafkaEvent event = FriendRequestKafkaEvent.of("friendship-service", payload);

        friendshipRequestEventConsumer.listen(event);

        verify(friendshipRealtimePort).publishRelationshipEvent(
                eq(senderId),
                eq(recipientId),
                eq(FriendRequestEvent.Type.ACCEPTED.name()),
                eq(payload),
                eq(RealtimeFlowId.FRIENDSHIP_REQUEST_ACCEPTED)
        );
    }

    @Test
    void duplicateFriendshipEvent_isDropped() {
        UUID userLow = UUID.randomUUID();
        UUID userHigh = UUID.randomUUID();
        FriendshipPayload payload = new FriendshipPayload(userLow, userHigh, userLow, "FRIENDS");
        FriendshipEvent event = FriendshipEvent.of("friendship-service", FriendshipEventType.FRIEND_REQUEST_DECLINED, payload);

        when(dedupeGuard.isDuplicate(event.getEventId())).thenReturn(true);

        friendshipEventConsumer.listen(event);

        verify(friendshipRealtimePort, never()).publishRelationshipEvent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
