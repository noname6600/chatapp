package com.example.chat.modules.message.application.command.impl;

import com.example.chat.modules.message.application.dto.request.ForwardMessageRequest;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessagePipeline;
import com.example.chat.modules.message.application.pipeline.edit.EditMessagePipeline;
import com.example.chat.modules.message.application.pipeline.send.SendMessagePipeline;
import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageCommandServiceForwardTest {

    @Mock
    private SendMessagePipeline sendPipeline;
    @Mock
    private EditMessagePipeline editPipeline;
    @Mock
    private DeleteMessagePipeline deletePipeline;
    @Mock
    private MessageMapper mapper;
    @Mock
    private IMessageEventPublisher messageEventPublisher;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatAttachmentRepository attachmentRepository;
    @Mock
    private RoomMemberRepository roomMemberRepository;
    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private MessageCommandService messageCommandService;

    private UUID actorId;
    private UUID sourceMessageId;
    private UUID targetRoomId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        sourceMessageId = UUID.randomUUID();
        targetRoomId = UUID.randomUUID();
    }

    @Test
    void forwardMessage_throwsForbidden_whenActorNotMemberOfTargetRoom() {
        ForwardMessageRequest request = ForwardMessageRequest.builder()
                .actorId(actorId)
                .sourceMessageId(sourceMessageId)
                .targetRoomId(targetRoomId)
                .build();

        when(roomMemberRepository.existsByRoomIdAndUserId(targetRoomId, actorId)).thenReturn(false);

        assertThatThrownBy(() -> messageCommandService.forwardMessage(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(messageRepository, never()).findById(sourceMessageId);
    }

    @Test
    void forwardMessage_throwsBadRequest_whenSourceMessageDeleted() {
        ForwardMessageRequest request = ForwardMessageRequest.builder()
                .actorId(actorId)
                .sourceMessageId(sourceMessageId)
                .targetRoomId(targetRoomId)
                .build();

        ChatMessage deletedSource = ChatMessage.builder()
                .id(sourceMessageId)
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.MIXED)
                .content("src")
                .deleted(true)
                .build();

        when(roomMemberRepository.existsByRoomIdAndUserId(targetRoomId, actorId)).thenReturn(true);
        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.of(deletedSource));

        assertThatThrownBy(() -> messageCommandService.forwardMessage(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);

        verify(attachmentRepository, never()).findByMessageId(sourceMessageId);
    }
}
