package com.chatweb.chat.modules.message.infrastructure.redis;

import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.event.factory.ChatMessagePayloadFactory;
import com.chatweb.chat.modules.message.event.factory.MessageDeletedPayloadFactory;
import com.chatweb.chat.modules.message.event.factory.MessageUpdatedPayloadFactory;
import com.chatweb.chat.modules.room.entity.Room;
import com.chatweb.chat.modules.room.enums.RoomType;
import com.chatweb.chat.modules.room.repository.RoomMemberRepository;
import com.chatweb.chat.modules.room.repository.RoomRepository;
import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.MessageDeletedPayload;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.common.kafka.producer.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageEventPublisherAdapterTest {

    @Mock
    private ChatRedisPublisher chatRedisPublisher;

    @Mock
    private ChatMessagePayloadFactory chatMessagePayloadFactory;

    @Mock
    private MessageUpdatedPayloadFactory messageUpdatedPayloadFactory;

    @Mock
    private MessageDeletedPayloadFactory messageDeletedPayloadFactory;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private ChatMessageEventPublisherAdapter adapter;

    @Test
    void publishMessageCreated_usesRoomKeyForKafkaPartitioning() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(31L)
                .content("hello")
                .deleted(false)
                .createdAt(Instant.now())
                .build();
        ChatMessagePayload payload = ChatMessagePayload.builder()
                .messageId(message.getId())
                .roomId(roomId)
                .senderId(senderId)
                .content("hello")
                .createdAt(Instant.now())
                .build();

        when(roomMemberRepository.findUserIdsByRoomId(roomId)).thenReturn(List.of(senderId));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(Room.builder().id(roomId).type(RoomType.PRIVATE).build()));
        when(roomMemberRepository.findByRoomIdAndUserId(roomId, senderId)).thenReturn(Optional.empty());
        when(chatMessagePayloadFactory.from(message, List.of(), List.of(), List.of(), true)).thenReturn(payload);

        adapter.publishMessageCreated(message, List.of(), List.of());

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaEventPublisher).publish(topicCaptor.capture(), keyCaptor.capture(), envelopeCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(ChatEventType.MESSAGE_SENT.value());
        assertThat(keyCaptor.getValue()).isEqualTo(roomId.toString());
        assertThat(envelopeCaptor.getValue().metadata().getEventType()).isEqualTo(ChatEventType.MESSAGE_SENT.value());
        assertThat(envelopeCaptor.getValue().payload()).isSameAs(payload);
    }

    @Test
    void publishMessageEdited_usesSharedMutationTopic() {
        UUID roomId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(UUID.randomUUID())
                .seq(32L)
                .content("edited")
                .deleted(false)
                .editedAt(Instant.now())
                .build();
        MessageUpdatedPayload payload = MessageUpdatedPayload.builder()
                .messageId(message.getId())
                .roomId(roomId)
                .seq(message.getSeq())
                .content(message.getContent())
                .editedAt(message.getEditedAt())
                .build();

        when(messageUpdatedPayloadFactory.from(message)).thenReturn(payload);

        adapter.publishMessageEdited(message);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaEventPublisher).publish(topicCaptor.capture(), keyCaptor.capture(), envelopeCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo("chat.message.events");
        assertThat(keyCaptor.getValue()).isEqualTo(roomId.toString());
        assertThat(envelopeCaptor.getValue().metadata().getEventType()).isEqualTo(ChatEventType.MESSAGE_EDITED.value());
        assertThat(envelopeCaptor.getValue().payload()).isSameAs(payload);
    }

    @Test
    void publishMessageDeleted_usesSharedMutationTopic() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(33L)
                .content("deleted")
                .deleted(true)
                .deletedBy(senderId)
                .build();
        MessageDeletedPayload payload = MessageDeletedPayload.builder()
                .messageId(message.getId())
                .roomId(roomId)
                .seq(message.getSeq())
                .deletedAt(Instant.now())
                .deletedBy(senderId)
                .build();

        when(messageDeletedPayloadFactory.from(message, senderId)).thenReturn(payload);

        adapter.publishMessageDeleted(message);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaEventPublisher).publish(topicCaptor.capture(), keyCaptor.capture(), envelopeCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo("chat.message.events");
        assertThat(keyCaptor.getValue()).isEqualTo(roomId.toString());
        assertThat(envelopeCaptor.getValue().metadata().getEventType()).isEqualTo(ChatEventType.MESSAGE_DELETED.value());
        assertThat(envelopeCaptor.getValue().payload()).isSameAs(payload);
    }
}
