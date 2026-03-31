package com.example.chat.modules.message.domain.model;

import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class MessageAggregate {

    private static final int MAX_ATTACHMENTS = 10;

    private static final long IMAGE_MAX_SIZE = 5L * 1024 * 1024;
    private static final long VIDEO_MAX_SIZE = 50L * 1024 * 1024;
    private static final long FILE_MAX_SIZE  = 50L * 1024 * 1024;

    private final ChatMessage message;
    private final List<ChatAttachment> attachments;

    public static MessageAggregate create(
            UUID roomId,
            UUID senderId,
            long seq,
            String content,
            UUID replyToMessageId,
            List<AttachmentDraft> drafts,
            String clientMessageId,
            String blocksJson
    ) {

        MessageType type = resolveType(content, drafts);

        UUID messageId = UUID.randomUUID();

        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .senderId(senderId)
                .seq(seq)
                .type(type)
                .content(content)
                .blocksJson(blocksJson)
                .replyToMessageId(replyToMessageId)
                .clientMessageId(clientMessageId)
                .deleted(false)
                .build();

        MessageAggregate aggregate = MessageAggregate.builder()
                .message(message)
                .attachments(new ArrayList<>())
                .build();

        aggregate.addAttachments(drafts);

        aggregate.validateMessage();

        return aggregate;
    }

    private static MessageType resolveType(
            String content,
            List<AttachmentDraft> attachments
    ) {

        boolean hasContent = content != null && !content.isBlank();
        boolean hasAttachments = attachments != null && !attachments.isEmpty();

        if (hasContent && hasAttachments) return MessageType.MIXED;
        if (hasAttachments) return MessageType.ATTACHMENT;
        if (hasContent) return MessageType.TEXT;

        throw new BusinessException(
                ErrorCode.MESSAGE_CONTENT_EMPTY,
                "Message cannot be empty"
        );
    }

    private void addAttachments(List<AttachmentDraft> drafts) {

        if (drafts == null || drafts.isEmpty()) return;

        if (drafts.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(
                    ErrorCode.TOO_MANY_ATTACHMENTS,
                    "Max attachments exceeded"
            );
        }

        for (AttachmentDraft draft : drafts) {

            validateDraft(draft);

            attachments.add(
                    ChatAttachment.builder()
                            .messageId(message.getId())
                            .type(draft.getType())
                            .url(draft.getUrl())
                            .publicId(draft.getPublicId())
                            .fileName(draft.getFileName())
                            .size(draft.getSize())
                            .width(draft.getWidth())
                            .height(draft.getHeight())
                            .duration(draft.getDuration())
                            .build()
            );
        }
    }

    private void validateDraft(AttachmentDraft draft) {

        if (draft == null) {
            throw new BusinessException(
                    ErrorCode.ATTACHMENT_INVALID,
                    "Attachment draft cannot be null"
            );
        }

        if (draft.getType() == null) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE,
                    "Attachment type required"
            );
        }

        if (draft.getUrl() == null || draft.getUrl().isBlank()) {
            throw new BusinessException(
                    ErrorCode.ATTACHMENT_INVALID,
                    "Attachment url required"
            );
        }

        if (draft.getPublicId() == null || draft.getPublicId().isBlank()) {
            throw new BusinessException(
                ErrorCode.ATTACHMENT_INVALID,
                "Attachment publicId required"
            );
        }

        if (!draft.getUrl().startsWith("https://res.cloudinary.com/")) {
            throw new BusinessException(
                ErrorCode.ATTACHMENT_INVALID,
                "Attachment url must be a Cloudinary secure URL"
            );
        }

        Long size = draft.getSize();
        AttachmentType type = draft.getType();

        switch (type) {

            case IMAGE:

                if (size != null && size > IMAGE_MAX_SIZE) {
                    throw new BusinessException(
                            ErrorCode.ATTACHMENT_TOO_LARGE,
                            "Image too large"
                    );
                }

                if (draft.getWidth() == null || draft.getHeight() == null) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Image width/height required"
                    );
                }

                break;

            case VIDEO:

                if (size != null && size > VIDEO_MAX_SIZE) {
                    throw new BusinessException(
                            ErrorCode.ATTACHMENT_TOO_LARGE,
                            "Video too large"
                    );
                }

                break;

            case FILE:

                if (size != null && size > FILE_MAX_SIZE) {
                    throw new BusinessException(
                            ErrorCode.ATTACHMENT_TOO_LARGE,
                            "File too large"
                    );
                }

                break;

            default:

                throw new BusinessException(
                        ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE,
                        "Unsupported attachment type"
                );
        }
    }

    private void validateMessage() {

        boolean hasContent =
                message.getContent() != null &&
                        !message.getContent().isBlank();

        boolean hasAttachments =
                attachments != null &&
                        !attachments.isEmpty();

        switch (message.getType()) {

            case TEXT:

                if (!hasContent)
                    throw new BusinessException(
                            ErrorCode.MESSAGE_CONTENT_EMPTY,
                            "TEXT must contain content"
                    );

                if (hasAttachments)
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "TEXT cannot contain attachments"
                    );

                break;

            case ATTACHMENT:

                if (!hasAttachments)
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "ATTACHMENT requires attachments"
                    );

                break;

            case MIXED:

                if (!hasContent || !hasAttachments)
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "MIXED requires text and attachment"
                    );

                break;
        }
    }

    public void editText(UUID actorId, String newContent) {
        editText(actorId, newContent, null);
    }

    public void editText(UUID actorId, String newContent, String blocksJson) {

        if (!message.getSenderId().equals(actorId))
            throw new BusinessException(
                    ErrorCode.PERMISSION_DENIED,
                    "Cannot edit other user's message"
            );

        if (message.getDeleted())
            throw new BusinessException(
                    ErrorCode.MESSAGE_DELETED,
                    "Cannot edit deleted message"
            );

        if (message.getType() == MessageType.ATTACHMENT)
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Attachment-only message cannot be edited"
            );

        if (newContent == null || newContent.isBlank())
            throw new BusinessException(
                    ErrorCode.MESSAGE_CONTENT_EMPTY,
                    "Content cannot be empty"
            );

        // Core update: always update content
        message.setContent(newContent);

        // Block handling:
        // If blocks are provided (from block-aware editing), ALWAYS preserve them exactly as-is
        // This maintains the editing user's intended ordering of text/media blocks
        if (blocksJson != null && !blocksJson.isBlank()) {
            // Store blocks exactly as provided, without any normalization/reordering
            message.setBlocksJson(blocksJson);
            // Ensure message is marked as MIXED since it has block structure
            message.setType(MessageType.MIXED);
        } else {
            // No blocks provided - handle as plain text edit
            message.setBlocksJson(null);
            
            boolean hasAttachments = attachments != null && !attachments.isEmpty();
            if (message.getType() == MessageType.TEXT) {
                message.setType(MessageType.TEXT);
            } else if (message.getType() == MessageType.MIXED) {
                // Normalize MIXED to TEXT if no blocks/attachments
                message.setType(hasAttachments ? MessageType.MIXED : MessageType.TEXT);
            }
        }

        markEdited();
    }


    public void markEdited() {

        if (message.getDeleted())
            throw new BusinessException(
                    ErrorCode.MESSAGE_DELETED,
                    "Message deleted"
            );

        message.setEditedAt(Instant.now());
    }

    public void delete(UUID actorId) {

        if (!message.getSenderId().equals(actorId))
            throw new BusinessException(
                    ErrorCode.PERMISSION_DENIED,
                    "Cannot delete other user's message"
            );

        if (message.getDeleted()) return;

        message.setDeleted(true);
        message.setDeletedAt(Instant.now());
        message.setDeletedBy(actorId);
    }
}