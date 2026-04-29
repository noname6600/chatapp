package com.example.chat.modules.message.application.command;

import com.example.chat.modules.message.application.command.impl.MessageCommandService;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.dto.request.MessageBlockRequest;
import com.example.chat.modules.message.application.dto.request.RoomInviteRequest;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessagePipeline;
import com.example.chat.modules.message.application.pipeline.edit.EditMessagePipeline;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.application.pipeline.send.SendMessagePipeline;
import com.example.chat.modules.message.domain.enums.MessageBlockType;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageCommandServiceTest {

    @Mock
    private SendMessagePipeline sendPipeline;
    @Mock
    private EditMessagePipeline editPipeline;
    @Mock
    private DeleteMessagePipeline deletePipeline;
    @Mock
    private MessageMapper mapper;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatAttachmentRepository attachmentRepository;
        @Mock
        private RoomMemberRepository roomMemberRepository;
        @Mock
        private RoomRepository roomRepository;

    @InjectMocks
    private MessageCommandService service;

    private UUID roomId;
    private UUID senderId;
    private String clientMessageId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        clientMessageId = "client-abc-123";
    }

    @Test
    void sendMessage_withDuplicateClientMessageId_returnsExistingMessage() {
        ChatMessage existingMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .seq(1L)
                .deleted(false)
                .build();

        MessageResponse expectedResponse = MessageResponse.builder()
                .messageId(existingMessage.getId())
                .clientMessageId(clientMessageId)
                .build();

        when(messageRepository.findByRoomIdAndClientMessageId(roomId, clientMessageId))
                .thenReturn(Optional.of(existingMessage));
        when(attachmentRepository.findByMessageId(existingMessage.getId()))
                .thenReturn(Collections.emptyList()); // Keep this for idempotency check path
        when(mapper.toResponse(eq(existingMessage), any(), any()))
                .thenReturn(expectedResponse);

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("hello")
                .clientMessageId(clientMessageId)
                .build();

        MessageResponse result = service.sendMessage(request);

        assertThat(result).isEqualTo(expectedResponse);
        verifyNoInteractions(sendPipeline);
    }

    @Test
    void sendMessage_withoutClientMessageId_skipsIdempotencyCheck() {
        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("hello")
                .build();

        ChatMessage savedMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(1L)
                .deleted(false)
                .build();
        when(sendPipeline.execute(any())).thenAnswer(invocation -> {
                        SendMessageContext ctx = invocation.getArgument(0);
                        ctx.setSavedMessage(savedMessage);
                        ctx.setSavedAttachments(List.of());
                        return ctx;
                });
        when(mapper.toResponse(eq(savedMessage), any(), any()))
                .thenReturn(MessageResponse.builder().build());

        service.sendMessage(request);

        verifyNoInteractions(messageRepository, attachmentRepository);
    }

    @Test
    void sendMessage_withNewClientMessageId_proceedsThroughPipeline() {
        when(messageRepository.findByRoomIdAndClientMessageId(eq(roomId), eq(clientMessageId)))
                .thenReturn(Optional.empty());

        ChatMessage savedMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(2L)
                .clientMessageId(clientMessageId)
                .deleted(false)
                .build();
        when(sendPipeline.execute(any())).thenAnswer(invocation -> {
                        SendMessageContext ctx = invocation.getArgument(0);
                        ctx.setSavedMessage(savedMessage);
                        ctx.setSavedAttachments(List.of());
                        return ctx;
                });
        when(mapper.toResponse(eq(savedMessage), any(), any()))
                .thenReturn(MessageResponse.builder().clientMessageId(clientMessageId).build());

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("hello")
                .clientMessageId(clientMessageId)
                .build();

        service.sendMessage(request);

        verify(sendPipeline).execute(any());
    }

    @Test
    void createMessage_withMentions_dropsNonMemberIds() {
        UUID validMention = UUID.randomUUID();
        UUID invalidMention = UUID.randomUUID();

        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, validMention))
                .thenReturn(true);
        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, invalidMention))
                .thenReturn(false);
        when(messageRepository.findByRoomIdAndClientMessageId(eq(roomId), eq(clientMessageId)))
                .thenReturn(Optional.empty());

        ChatMessage savedMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(2L)
                .clientMessageId(clientMessageId)
                .deleted(false)
                .build();

        when(sendPipeline.execute(any())).thenAnswer(invocation -> {
            SendMessageContext ctx = invocation.getArgument(0);
            assertThat(ctx.getRequest().getMentionedUserIds()).containsExactly(validMention);
            ctx.setSavedMessage(savedMessage);
            ctx.setSavedAttachments(List.of());
            return ctx;
        });
        when(mapper.toResponse(eq(savedMessage), any(), any()))
                .thenReturn(MessageResponse.builder().clientMessageId(clientMessageId).build());

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("hello")
                .clientMessageId(clientMessageId)
                .mentionedUserIds(List.of(validMention, invalidMention, validMention))
                .build();

        service.sendMessage(request);

        verify(sendPipeline).execute(any());
        assertThat(request.getMentionedUserIds()).containsExactly(validMention);
    }

    @Test
    void sendMessage_withRoomInvite_forNonMemberSender_throwsForbidden() {
        UUID inviteRoomId = UUID.randomUUID();

        when(roomRepository.findById(inviteRoomId))
                .thenReturn(Optional.of(Room.builder().id(inviteRoomId).type(RoomType.GROUP).name("Dev Room").build()));
        when(roomMemberRepository.existsByRoomIdAndUserId(inviteRoomId, senderId))
                .thenReturn(false);

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("invite")
                .clientMessageId(clientMessageId)
                .blocks(List.of(MessageBlockRequest.builder()
                        .type(MessageBlockType.ROOM_INVITE)
                        .roomInvite(RoomInviteRequest.builder().roomId(inviteRoomId).build())
                        .build()))
                .build();

        assertThatThrownBy(() -> service.sendMessage(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        verifyNoInteractions(sendPipeline);
    }

    @Test
    void sendMessage_withRoomInvite_forPrivateRoom_throwsBadRequest() {
        UUID inviteRoomId = UUID.randomUUID();

        when(roomRepository.findById(inviteRoomId))
                .thenReturn(Optional.of(Room.builder().id(inviteRoomId).type(RoomType.PRIVATE).name("Private Room").build()));

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("invite")
                .clientMessageId(clientMessageId)
                .blocks(List.of(MessageBlockRequest.builder()
                        .type(MessageBlockType.ROOM_INVITE)
                        .roomInvite(RoomInviteRequest.builder().roomId(inviteRoomId).build())
                        .build()))
                .build();

        assertThatThrownBy(() -> service.sendMessage(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.BAD_REQUEST);

        verifyNoInteractions(sendPipeline);
    }

    @Test
    void sendMessage_withRoomInvite_enrichesSnapshotMetadataBeforePipeline() {
        UUID inviteRoomId = UUID.randomUUID();
        Room inviteRoom = Room.builder()
                .id(inviteRoomId)
                .type(RoomType.GROUP)
                .name("Design Guild")
                .avatarUrl("https://example.com/room.png")
                .build();

        when(messageRepository.findByRoomIdAndClientMessageId(eq(roomId), eq(clientMessageId)))
                .thenReturn(Optional.empty());
        when(roomRepository.findById(inviteRoomId)).thenReturn(Optional.of(inviteRoom));
        when(roomMemberRepository.existsByRoomIdAndUserId(inviteRoomId, senderId)).thenReturn(true);
        when(roomMemberRepository.countByRoomId(inviteRoomId)).thenReturn(7L);

        ChatMessage savedMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(3L)
                .clientMessageId(clientMessageId)
                .deleted(false)
                .build();

        when(sendPipeline.execute(any())).thenAnswer(invocation -> {
            SendMessageContext ctx = invocation.getArgument(0);
            MessageBlockRequest inviteBlock = ctx.getRequest().getBlocks().get(0);
            assertThat(inviteBlock.getRoomInvite().getRoomName()).isEqualTo("Design Guild");
            assertThat(inviteBlock.getRoomInvite().getRoomAvatarUrl()).isEqualTo("https://example.com/room.png");
            assertThat(inviteBlock.getRoomInvite().getMemberCount()).isEqualTo(7);
            ctx.setSavedMessage(savedMessage);
            ctx.setSavedAttachments(List.of());
            return ctx;
        });
        when(mapper.toResponse(eq(savedMessage), any(), any()))
                .thenReturn(MessageResponse.builder().clientMessageId(clientMessageId).build());

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("invite")
                .clientMessageId(clientMessageId)
                .blocks(List.of(MessageBlockRequest.builder()
                        .type(MessageBlockType.ROOM_INVITE)
                        .roomInvite(RoomInviteRequest.builder().roomId(inviteRoomId).build())
                        .build()))
                .build();

        service.sendMessage(request);

        verify(sendPipeline).execute(any());
    }
}


