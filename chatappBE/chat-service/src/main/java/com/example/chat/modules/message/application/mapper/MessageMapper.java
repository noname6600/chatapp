package com.example.chat.modules.message.application.mapper;

import com.example.chat.modules.message.application.dto.response.AttachmentResponse;
import com.example.chat.modules.message.application.dto.response.MessageBlockResponse;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.dto.response.ReactionResponse;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.chat.modules.message.domain.repository.projection.MessageReactionSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MessageMapper {

        private final MessageBlockMapper messageBlockMapper;

    public MessageResponse toResponse(MessageAggregate aggregate) {

        return toResponse(
                aggregate.getMessage(),
                aggregate.getAttachments(),
                Collections.emptyList()
        );
    }

    public MessageResponse toResponse(
            ChatMessage message,
            List<ChatAttachment> attachments,
            List<MessageReactionSummaryProjection> reactions
    ) {

        List<AttachmentResponse> attachmentResponses =
                mapAttachments(attachments);

        List<MessageBlockResponse> blockResponses =
                messageBlockMapper.toResponses(message.getBlocksJson());

        List<ReactionResponse> reactionResponses =
                mapReactions(reactions);

        return MessageResponse.builder()
                .messageId(message.getId())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .seq(message.getSeq())
                .type(message.getType())
                .content(message.getContent())
                .replyToMessageId(message.getReplyToMessageId())
                .clientMessageId(message.getClientMessageId())
                .createdAt(message.getCreatedAt())
                .editedAt(message.getEditedAt())
                .deleted(message.getDeleted())
                .attachments(attachmentResponses)
                .blocks(blockResponses)
                .reactions(reactionResponses)
                .build();
    }

    private List<AttachmentResponse> mapAttachments(
            List<ChatAttachment> attachments
    ) {

        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }

        return attachments.stream()
                .map(this::toAttachmentResponse)
                .toList();
    }

    private AttachmentResponse toAttachmentResponse(
            ChatAttachment attachment
    ) {

        return AttachmentResponse.builder()
                .id(attachment.getId())
                .type(attachment.getType())
                .url(attachment.getUrl())
                .publicId(attachment.getPublicId())
                .fileName(attachment.getFileName())
                .size(attachment.getSize())
                .width(attachment.getWidth())
                .height(attachment.getHeight())
                .duration(attachment.getDuration())
                .createdAt(attachment.getCreatedAt())
                .build();
    }

    private List<ReactionResponse> mapReactions(
            List<MessageReactionSummaryProjection> reactions
    ) {

        if (reactions == null || reactions.isEmpty()) {
            return Collections.emptyList();
        }

        return reactions.stream()
                .map(this::toReactionResponse)
                .toList();
    }

    private ReactionResponse toReactionResponse(
                        MessageReactionSummaryProjection reaction
    ) {

        return ReactionResponse.builder()
                .emoji(reaction.getEmoji())
                .count(reaction.getCount())
                                .reactedByMe(Boolean.TRUE.equals(reaction.getReactedByMe()))
                .build();
    }
}