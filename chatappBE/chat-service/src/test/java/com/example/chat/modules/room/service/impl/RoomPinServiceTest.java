package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.entity.RoomPinnedMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.repository.RoomPinnedMessageRepository;
import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomPinServiceTest {

    @Mock
    private RoomMemberRepository roomMemberRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatAttachmentRepository chatAttachmentRepository;
    @Mock
    private RoomPinnedMessageRepository roomPinnedMessageRepository;
    @Mock
    private IMessageEventPublisher messageEventPublisher;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ChatRedisPublisher chatRedisPublisher;

    @InjectMocks
    private RoomPinService roomPinService;

    private UUID roomId;
    private UUID actorId;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        messageId = UUID.randomUUID();
    }

    @Test
    void pinMessage_throwsForbidden_whenActorNotRoomMember() {
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, actorId)).thenReturn(false);

        assertThatThrownBy(() -> roomPinService.pinMessage(roomId, actorId, messageId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(roomPinnedMessageRepository, never()).save(org.mockito.ArgumentMatchers.any(RoomPinnedMessage.class));
    }

    @Test
    void pinMessage_throwsConflict_whenMessageAlreadyPinned() {
        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(actorId)
                .seq(1L)
                .type(MessageType.TEXT)
                .content("hello")
                .deleted(false)
                .build();

        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, actorId)).thenReturn(true);
        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(roomPinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId))
                .thenReturn(Optional.of(RoomPinnedMessage.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> roomPinService.pinMessage(roomId, actorId, messageId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(roomPinnedMessageRepository, never()).save(org.mockito.ArgumentMatchers.any(RoomPinnedMessage.class));
    }
}
