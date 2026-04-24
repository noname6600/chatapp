package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.service.IMessagePreviewService;
import com.example.chat.modules.room.service.IRoomService;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

        // Kafka publish is best-effort: fire async so broker unavailability
        // never blocks the send response. The message is already persisted.
        final ChatMessage msg = savedMessage;
        final List<ChatAttachment> attachments = persistedAttachments;
        final List<java.util.UUID> mentions = context.getMentionedUsers();
        CompletableFuture.runAsync(() -> {
            try {
                eventPublisher.publishMessageCreated(msg, attachments, mentions);
            } catch (Exception e) {
                log.warn("Failed to publish message event for messageId={}: {}",
                        msg.getId(), e.getMessage());
            }
        });
    }

    @Override
    public long timeoutMs() {
        // Only updateLastMessage (fast DB write) runs in the pipeline timeout window.
        // Kafka publish is async-fire-and-forget above, so keep timeout short.
        return 10_000;
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistMentionStep.class
        };
    }
}
