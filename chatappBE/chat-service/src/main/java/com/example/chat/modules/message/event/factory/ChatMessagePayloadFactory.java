package com.example.chat.modules.message.event.factory;

import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.service.IMessagePreviewService;
import com.example.chat.modules.message.application.mapper.MessageBlockMapper;
import com.example.chat.modules.message.event.mapper.AttachmentTypeMapper;
import com.example.chat.modules.message.event.mapper.MessageTypeMapper;
import com.example.common.integration.chat.AttachmentPayload;
import com.example.common.integration.chat.ChatMessagePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatMessagePayloadFactory {

    private final IMessagePreviewService previewService;
        private final MessageBlockMapper messageBlockMapper;

    public ChatMessagePayload from(
            ChatMessage message,
            List<ChatAttachment> attachments,
            List<UUID> mentionedUserIds,
            List<UUID> recipientUserIds
    ) {

        List<AttachmentPayload> attachmentPayloads =
                attachments == null
                        ? List.of()
                        : attachments.stream()
                        .map(this::mapAttachment)
                        .toList();

        return ChatMessagePayload.builder()
                .messageId(message.getId())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .seq(message.getSeq())
                .type(MessageTypeMapper.toEvent(message.getType()))
                .content(message.getContent())
                .replyToMessageId(message.getReplyToMessageId())
                .clientMessageId(message.getClientMessageId())
                .createdAt(message.getCreatedAt())
                .preview(previewService.buildPreview(message, attachments))
                .attachments(attachmentPayloads)
                .blocks(messageBlockMapper.toPayloads(message.getBlocksJson()))
                .mentionedUserIds(mentionedUserIds == null ? List.of() : mentionedUserIds)
                .recipientUserIds(recipientUserIds == null ? List.of() : recipientUserIds)
                .build();
    }

    private AttachmentPayload mapAttachment(ChatAttachment a) {

        return AttachmentPayload.builder()
                .id(a.getId())
                .type(AttachmentTypeMapper.toEvent(a.getType()))
                .url(a.getUrl())
                .publicId(a.getPublicId())
                .fileName(a.getFileName())
                .size(a.getSize())
                .width(a.getWidth())
                .height(a.getHeight())
                .duration(a.getDuration())
                .build();
    }
}