package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.service.IMessagePreviewService;
import com.example.chat.modules.room.service.IRoomService;
import com.example.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

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

        eventPublisher.publishMessageCreated(
                savedMessage,
                persistedAttachments,
                context.getMentionedUsers()
        );
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                PersistMentionStep.class
        };
    }
}