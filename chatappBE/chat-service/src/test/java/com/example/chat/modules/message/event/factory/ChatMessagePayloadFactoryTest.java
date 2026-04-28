package com.example.chat.modules.message.event.factory;

import com.example.chat.modules.message.application.mapper.MessageBlockMapper;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.service.IMessagePreviewService;
import com.example.chat.modules.message.infrastructure.client.UserClient;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.common.integration.chat.ChatMessagePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessagePayloadFactoryTest {

    @Mock
    private IMessagePreviewService previewService;

    @Test
    void from_mapsCreatedAtFromPersistedMessage() {
        Instant createdAt = Instant.now();
        ChatMessagePayloadFactory factory = new ChatMessagePayloadFactory(
                previewService,
                new MessageBlockMapper(new ObjectMapper()),
                org.mockito.Mockito.mock(ChatMessageRepository.class),
                org.mockito.Mockito.mock(RoomMemberRepository.class),
                org.mockito.Mockito.mock(UserClient.class)
        );

        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .content("hello")
                .createdAt(createdAt)
                .deleted(false)
                .build();

        when(previewService.buildPreview(message, List.of())).thenReturn("hello");

        ChatMessagePayload payload = factory.from(message, List.of(), List.of(), List.of(), false);

        assertThat(payload.getCreatedAt()).isEqualTo(createdAt);
        assertThat(payload.getBlocks()).isEmpty();
        assertThat(payload.getMentionedUserIds()).isEmpty();
        assertThat(payload.getRecipientUserIds()).isEmpty();
    }

    @Test
    void from_invalidBlocksJson_fallsBackToEmptyBlocks() {
        ChatMessagePayloadFactory factory = new ChatMessagePayloadFactory(
                previewService,
                new MessageBlockMapper(new ObjectMapper()),
                org.mockito.Mockito.mock(ChatMessageRepository.class),
                org.mockito.Mockito.mock(RoomMemberRepository.class),
                org.mockito.Mockito.mock(UserClient.class)
        );

        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.MIXED)
                .content("hello")
                .blocksJson("{not-json")
                .deleted(false)
                .build();

        when(previewService.buildPreview(message, List.of())).thenReturn("hello");

        ChatMessagePayload payload = factory.from(message, List.of(), List.of(), List.of(), false);

        assertThat(payload.getBlocks()).isEmpty();
    }
}
