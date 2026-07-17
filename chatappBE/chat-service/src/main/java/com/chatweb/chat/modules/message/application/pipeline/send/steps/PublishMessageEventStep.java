package com.chatweb.chat.modules.message.application.pipeline.send.steps;

import com.chatweb.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.chatweb.chat.modules.message.application.service.IMessageEventPublisher;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.service.IMessagePreviewService;
import com.chatweb.chat.modules.room.service.IRoomService;
import com.chatweb.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.chatweb.chat.support.TransactionPublisher;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublishMessageEventStep
        implements PipelineStep<SendMessageContext> {

    private final IMessageEventPublisher eventPublisher;
    private final IRoomService roomService;
    private final IMessagePreviewService previewService;

    @Override
    public void execute(SendMessageContext context) {

        ChatMessage savedMessage = context.getSavedMessage();
        if (savedMessage == null) {
            throw new IllegalStateException(
                "Saved message missing"
            );
        }

        List<ChatAttachment> persistedAttachments = context.getSavedAttachments();
        if (persistedAttachments == null) {
            persistedAttachments = List.of();
        }

        String preview = previewService.buildPreview(savedMessage, persistedAttachments);

        // Update room projection synchronously so /rooms/my has fresh lastMessage without waiting for Kafka.
        roomService.updateLastMessage(
                savedMessage.getRoomId(),
                savedMessage.getId(),
                savedMessage.getSenderId(),
                savedMessage.getCreatedAt(),
                preview,
                savedMessage.getSeq()
        );

        // Sender should never see their own sent message as unread after refresh.
        roomService.markRoomRead(savedMessage.getRoomId(), savedMessage.getSenderId());

        final var finalAttachments = persistedAttachments;
        final var mentions = context.getMentionedUsers();
        TransactionPublisher.publishAfterCommit(() -> {
            try {
                eventPublisher.publishMessageCreated(savedMessage, finalAttachments, mentions);
            } catch (Exception e) {
                log.warn("Failed to publish message event for messageId={}: {}",
                        savedMessage.getId(), e.getMessage());
            }
        });
    }

    @Override
    public long timeoutMs() {
        // Only local DB projection updates run inside the pipeline execution path.
        // Message event publication runs after commit when transaction synchronization is active.
        return 10_000;
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistMentionStep.class
        };
    }
}
