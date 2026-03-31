package com.example.chat.modules.message.infrastructure.service;

import com.example.chat.modules.message.application.mapper.MessageBlockMapper;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageType;

import com.example.chat.modules.message.domain.service.IMessagePreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultMessagePreviewService
        implements IMessagePreviewService {

    private final MessageBlockMapper messageBlockMapper;

    @Override
    public String buildPreview(
            ChatMessage message,
            List<ChatAttachment> attachments
    ) {

        if (message.getBlocksJson() != null && !message.getBlocksJson().isBlank()) {
            String preview = messageBlockMapper.buildPreviewText(message.getBlocksJson());
            if (!preview.isBlank()) {
                return truncate(preview);
            }
        }

        boolean hasAttachments =
                attachments != null && !attachments.isEmpty();

        boolean hasText =
                message.getContent() != null &&
                        !message.getContent().isBlank();

        MessageType type = message.getType();

        return switch (type) {

            case TEXT -> truncate(message.getContent());

            case ATTACHMENT -> buildAttachmentPreview(attachments);

            case MIXED -> {
                if (hasText) {
                    yield truncate(message.getContent());
                }
                yield hasAttachments ? "Attachment" : "";
            }

            default -> "";
        };
    }

    private String buildAttachmentPreview(List<ChatAttachment> attachments) {

        if (attachments == null || attachments.isEmpty()) {
            return "Attachment";
        }

        long imageCount = attachments.stream()
                .filter(a -> a.getType() == AttachmentType.IMAGE)
                .count();

        if (imageCount > 1) {
            return imageCount + " images";
        }

        if (imageCount == 1) {
            return "Image";
        }

        return "Attachment";
    }

    private String truncate(String text) {

        if (text == null) {
            return "";
        }

        return text.length() > 80
                ? text.substring(0, 77) + "..."
                : text;
    }
}