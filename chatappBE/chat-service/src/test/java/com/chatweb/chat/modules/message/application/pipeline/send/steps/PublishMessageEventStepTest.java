package com.chatweb.chat.modules.message.application.pipeline.send.steps;

import com.chatweb.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.chatweb.chat.modules.message.application.service.IMessageEventPublisher;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.service.IMessagePreviewService;
import com.chatweb.chat.modules.room.service.IRoomService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishMessageEventStepTest {

    private final IMessageEventPublisher eventPublisher = mock(IMessageEventPublisher.class);
    private final IRoomService roomService = mock(IRoomService.class);
    private final IMessagePreviewService previewService = mock(IMessagePreviewService.class);
    private final PublishMessageEventStep step = new PublishMessageEventStep(eventPublisher, roomService, previewService);

    @AfterEach
    void cleanupTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void execute_registersAfterCommitPublish_whenTransactionSynchronizationActive() {
        SendMessageContext context = buildContext();
        ChatMessage savedMessage = context.getSavedMessage();
        List<ChatAttachment> savedAttachments = context.getSavedAttachments();

        when(previewService.buildPreview(savedMessage, savedAttachments)).thenReturn("preview");

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        step.execute(context);

        verify(eventPublisher, never()).publishMessageCreated(any(), anyList(), anyList());
        verify(roomService).updateLastMessage(
                savedMessage.getRoomId(),
                savedMessage.getId(),
                savedMessage.getSenderId(),
                savedMessage.getCreatedAt(),
                "preview",
                savedMessage.getSeq()
        );
        verify(roomService).markRoomRead(savedMessage.getRoomId(), savedMessage.getSenderId());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(eventPublisher, times(1)).publishMessageCreated(savedMessage, savedAttachments, context.getMentionedUsers());
    }

    @Test
    void execute_publishesImmediately_whenNoTransactionSynchronization() {
        SendMessageContext context = buildContext();
        ChatMessage savedMessage = context.getSavedMessage();
        List<ChatAttachment> savedAttachments = context.getSavedAttachments();

        when(previewService.buildPreview(savedMessage, savedAttachments)).thenReturn("preview");

        step.execute(context);

        verify(eventPublisher).publishMessageCreated(savedMessage, savedAttachments, context.getMentionedUsers());
    }

    @Test
    void execute_doesNotThrow_whenAfterCommitPublishFails() {
        SendMessageContext context = buildContext();
        ChatMessage savedMessage = context.getSavedMessage();
        List<ChatAttachment> savedAttachments = context.getSavedAttachments();

        when(previewService.buildPreview(savedMessage, savedAttachments)).thenReturn("preview");
        doThrow(new RuntimeException("kafka unavailable"))
                .when(eventPublisher)
                .publishMessageCreated(savedMessage, savedAttachments, context.getMentionedUsers());

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        step.execute(context);

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(eventPublisher).publishMessageCreated(savedMessage, savedAttachments, context.getMentionedUsers());
    }

    private SendMessageContext buildContext() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID mentionedUser = UUID.randomUUID();

        ChatMessage savedMessage = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(10L)
                .content("hello")
                .createdAt(Instant.now())
                .build();

        ChatAttachment attachment = ChatAttachment.builder()
                .messageId(messageId)
                .url("https://res.cloudinary.com/demo/image/upload/sample.jpg")
                .build();

        SendMessageContext context = new SendMessageContext();
        context.setSavedMessage(savedMessage);
        context.setSavedAttachments(List.of(attachment));
        context.setMentionedUsers(List.of(mentionedUser));
        return context;
    }
}
