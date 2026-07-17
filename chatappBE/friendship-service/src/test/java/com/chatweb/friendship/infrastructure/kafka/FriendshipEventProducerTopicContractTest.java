package com.chatweb.friendship.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import com.chatweb.common.kafka.topic.KafkaTopics;
import com.chatweb.friendship.entity.Friendship;
import com.chatweb.friendship.enums.FriendshipStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FriendshipEventProducerTopicContractTest {

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private FriendshipEventProducer producer;

    @Test
    void publish_requestEvents_toFriendshipRequestAggregateTopic() {
        Friendship friendship = buildFriendship();
        ReflectionTestUtils.setField(producer, "sourceService", "friendship-service");

        producer.publish(FriendshipEventType.FRIEND_REQUEST_SENT, friendship);

        verify(kafkaEventPublisher).publish(eq(KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS), anyString(), any());
    }

    @Test
    void publish_statusEvents_toFriendshipAggregateTopic() {
        Friendship friendship = buildFriendship();
        ReflectionTestUtils.setField(producer, "sourceService", "friendship-service");

        producer.publish(FriendshipEventType.FRIEND_UNFRIENDED, friendship);

        verify(kafkaEventPublisher).publish(eq(KafkaTopics.TOPIC_FRIENDSHIP_EVENTS), anyString(), any());
    }

    @Test
    void publish_requestEvents_useIdFirstSenderDisplayName_withoutSynchronousUserLookup() {
        Friendship friendship = buildFriendship();
        ReflectionTestUtils.setField(producer, "sourceService", "friendship-service");

        producer.publish(FriendshipEventType.FRIEND_REQUEST_SENT, friendship);

        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaEventPublisher).publish(eq(KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS), eq(friendship.getId().toString()), envelopeCaptor.capture());

        EventEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope).isNotNull();
        assertThat(envelope.payload()).isInstanceOf(FriendRequestPayload.class);

        FriendRequestPayload payload = (FriendRequestPayload) envelope.payload();
        assertThat(payload.getSenderId()).isEqualTo(friendship.getActionUserId());
        assertThat(payload.getSenderDisplayName()).isEqualTo(friendship.getActionUserId().toString());
    }

    private Friendship buildFriendship() {
        UUID userLow = UUID.randomUUID();
        UUID userHigh = UUID.randomUUID();
        Instant now = Instant.now();

        return Friendship.builder()
                .id(UUID.randomUUID())
                .userLow(userLow)
                .userHigh(userHigh)
                .actionUserId(userLow)
                .status(FriendshipStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
