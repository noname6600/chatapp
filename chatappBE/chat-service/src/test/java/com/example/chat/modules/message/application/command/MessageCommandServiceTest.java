package com.example.chat.modules.message.application.command;

import com.example.chat.modules.message.application.command.impl.MessageCommandService;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessagePipeline;
import com.example.chat.modules.message.application.pipeline.edit.EditMessagePipeline;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.application.pipeline.send.SendMessagePipeline;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
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
}
