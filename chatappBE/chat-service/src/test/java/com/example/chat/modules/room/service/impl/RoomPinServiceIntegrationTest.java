package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.service.ISystemMessageService;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.entity.RoomPinnedMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.repository.RoomPinnedMessageRepository;
import com.example.chat.modules.message.domain.service.IMessagePreviewService;
import com.example.chat.modules.message.infrastructure.service.DefaultMessagePreviewService;
import com.example.chat.realtime.port.ChatRealtimePort;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ContextConfiguration(classes = RoomPinServiceIntegrationTest.TestApplication.class)
class RoomPinServiceIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomMemberRepository roomMemberRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatAttachmentRepository chatAttachmentRepository;
    @Autowired
    private RoomPinnedMessageRepository roomPinnedMessageRepository;

    private RoomPinService roomPinService;

    private ISystemMessageService systemMessageService;
    private MessageMapper messageMapper;
        private IMessagePreviewService previewService;

    @BeforeEach
    void setUp() {
        systemMessageService = mock(ISystemMessageService.class);
        messageMapper = mock(MessageMapper.class);
        previewService = new DefaultMessagePreviewService(new com.example.chat.modules.message.application.mapper.MessageBlockMapper(new ObjectMapper()));

        when(messageMapper.toResponse(any(ChatMessage.class), any(List.class), any(List.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    return MessageResponse.builder()
                            .messageId(message.getId())
                            .roomId(message.getRoomId())
                            .senderId(message.getSenderId())
                            .seq(message.getSeq())
                            .type(message.getType())
                            .content(message.getContent())
                            .build();
                });

        roomPinService = new RoomPinService(
                roomMemberRepository,
                chatMessageRepository,
                chatAttachmentRepository,
                roomPinnedMessageRepository,
                systemMessageService,
                messageMapper,
                                mock(ChatRealtimePort.class),
                previewService
        );
    }

    @Test
    void pinMessage_happyPathAndDuplicateAndNonMember() {
        UUID roomId = createRoomWithMember();
        UUID actorId = UUID.randomUUID();
        addMember(roomId, actorId);
        UUID messageId = createMessage(roomId, UUID.randomUUID(), 1L, false);

        roomPinService.pinMessage(roomId, actorId, messageId);

        assertThat(roomPinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId)).isPresent();
        assertThat(roomPinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId)
                .map(RoomPinnedMessage::getPreviewText))
                .contains("message 1");

        assertThatThrownBy(() -> roomPinService.pinMessage(roomId, actorId, messageId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.CONFLICT);

        UUID outsiderId = UUID.randomUUID();
        UUID messageId2 = createMessage(roomId, UUID.randomUUID(), 2L, false);

        assertThatThrownBy(() -> roomPinService.pinMessage(roomId, outsiderId, messageId2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void unpinMessage_removesExistingPin() {
        UUID roomId = createRoomWithMember();
        UUID actorId = UUID.randomUUID();
        addMember(roomId, actorId);
        UUID messageId = createMessage(roomId, UUID.randomUUID(), 1L, false);

        roomPinnedMessageRepository.save(RoomPinnedMessage.builder()
                .roomId(roomId)
                .messageId(messageId)
                .pinnedBy(actorId)
                .pinnedAt(Instant.now())
                .build());

        roomPinService.unpinMessage(roomId, actorId, messageId);

        assertThat(roomPinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId)).isEmpty();
    }

    @Test
    void getPinnedMessages_returnsPinnedMessagesInPinnedAtDescOrder() {
        UUID roomId = createRoomWithMember();
        UUID actorId = UUID.randomUUID();
        addMember(roomId, actorId);

        UUID olderMessageId = createMessage(roomId, UUID.randomUUID(), 1L, false);
        UUID newerMessageId = createMessage(roomId, UUID.randomUUID(), 2L, false);

        roomPinnedMessageRepository.save(RoomPinnedMessage.builder()
                .roomId(roomId)
                .messageId(olderMessageId)
                .pinnedBy(actorId)
                .pinnedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build());
        roomPinnedMessageRepository.save(RoomPinnedMessage.builder()
                .roomId(roomId)
                .messageId(newerMessageId)
                .pinnedBy(actorId)
                .pinnedAt(Instant.parse("2026-01-01T00:01:00Z"))
                .build());

        List<MessageResponse> pinned = roomPinService.getPinnedMessages(roomId, actorId);

        assertThat(pinned).hasSize(2);
        assertThat(pinned.get(0).getMessageId()).isEqualTo(newerMessageId);
        assertThat(pinned.get(1).getMessageId()).isEqualTo(olderMessageId);
    }

        @Test
        void getPinnedMessages_prefersStoredPreviewTextWithLegacyFallback() {
                UUID roomId = createRoomWithMember();
                UUID actorId = UUID.randomUUID();
                addMember(roomId, actorId);

                UUID messageId = createMessage(roomId, UUID.randomUUID(), 1L, false);
                UUID legacyMessageId = createMessage(roomId, UUID.randomUUID(), 2L, false);

                roomPinnedMessageRepository.save(RoomPinnedMessage.builder()
                                .roomId(roomId)
                                .messageId(messageId)
                                .pinnedBy(actorId)
                                .previewKind("IMAGE")
                                .previewText("Image")
                                .pinnedAt(Instant.parse("2026-01-01T00:02:00Z"))
                                .build());
                roomPinnedMessageRepository.save(RoomPinnedMessage.builder()
                                .roomId(roomId)
                                .messageId(legacyMessageId)
                                .pinnedBy(actorId)
                                .pinnedAt(Instant.parse("2026-01-01T00:01:00Z"))
                                .build());

                List<MessageResponse> pinned = roomPinService.getPinnedMessages(roomId, actorId);

                assertThat(pinned).hasSize(2);
                assertThat(pinned.get(0).getContent()).isEqualTo("Image");
                assertThat(pinned.get(1).getContent()).isEqualTo("message 2");
        }

    private UUID createRoomWithMember() {
        Room room = roomRepository.save(Room.builder()
                .type(RoomType.GROUP)
                .name("Room")
                .createdBy(UUID.randomUUID())
                .build());
        return room.getId();
    }

    private void addMember(UUID roomId, UUID userId) {
        roomMemberRepository.save(RoomMember.builder()
                .roomId(roomId)
                .userId(userId)
                .role(Role.MEMBER)
                .build());
    }

    private UUID createMessage(UUID roomId, UUID senderId, long seq, boolean deleted) {
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(seq)
                .type(MessageType.TEXT)
                .content("message " + seq)
                .deleted(deleted)
                .build());
        return message.getId();
    }

    @SpringBootConfiguration
    @EntityScan(basePackages = "com.example.chat.modules")
    @EnableJpaRepositories(basePackages = "com.example.chat.modules")
    static class TestApplication {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .build();
        }
    }
}


