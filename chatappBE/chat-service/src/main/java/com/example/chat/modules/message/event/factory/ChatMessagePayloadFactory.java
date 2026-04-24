package com.example.chat.modules.message.event.factory;

import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.service.IMessagePreviewService;
import com.example.chat.modules.message.application.mapper.MessageBlockMapper;
import com.example.chat.modules.message.infrastructure.client.UserBasicProfile;
import com.example.chat.modules.message.infrastructure.client.UserClient;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.message.event.mapper.AttachmentTypeMapper;
import com.example.chat.modules.message.event.mapper.MessageTypeMapper;
import com.example.chat.modules.room.entity.RoomMember;
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
    private final ChatMessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserClient userClient;

    public ChatMessagePayload from(
            ChatMessage message,
            List<ChatAttachment> attachments,
            List<UUID> mentionedUserIds,
            List<UUID> recipientUserIds,
            boolean isDirect
    ) {

        List<AttachmentPayload> attachmentPayloads =
                attachments == null
                        ? List.of()
                        : attachments.stream()
                        .map(this::mapAttachment)
                        .toList();

        UUID replyToAuthorId = null;
        if (message.getReplyToMessageId() != null) {
            replyToAuthorId = messageRepository.findById(message.getReplyToMessageId())
                    .map(ChatMessage::getSenderId)
                    .orElse(null);
        }

        RoomMember member = roomMemberRepository.findByRoomIdAndUserId(message.getRoomId(), message.getSenderId())
                .orElse(null);

        String senderDisplayName = member == null ? null : member.getDisplayName();

        // Backfill display name for older group memberships that were created without it.
        if (senderDisplayName == null || senderDisplayName.isBlank()) {
            UserBasicProfile basic = safeGetBasicProfile(message.getSenderId());
            if (basic != null && basic.getDisplayName() != null && !basic.getDisplayName().isBlank()) {
                senderDisplayName = basic.getDisplayName();

                if (member != null) {
                    member.setDisplayName(senderDisplayName);
                    if (basic.getAvatarUrl() != null && !basic.getAvatarUrl().isBlank()) {
                        member.setAvatarUrl(basic.getAvatarUrl());
                    }
                    roomMemberRepository.save(member);
                }
            }
        }

        return ChatMessagePayload.builder()
                .messageId(message.getId())
                .roomId(message.getRoomId())
                .senderId(message.getSenderId())
                .seq(message.getSeq())
                .type(MessageTypeMapper.toEvent(message.getType()))
                .content(message.getContent())
                .replyToMessageId(message.getReplyToMessageId())
                .forwardedFromMessageId(message.getForwardedFromMessageId())
                .systemEventType(message.getSystemEventType() == null ? null : message.getSystemEventType().name())
                .actorUserId(message.getActorUserId())
                .targetMessageId(message.getTargetMessageId())
                .replyToAuthorId(replyToAuthorId)
                .clientMessageId(message.getClientMessageId())
                .createdAt(message.getCreatedAt())
                .senderDisplayName(senderDisplayName)
                .preview(previewService.buildPreview(message, attachments))
                .attachments(attachmentPayloads)
                .blocks(messageBlockMapper.toPayloads(message.getBlocksJson()))
                .mentionedUserIds(mentionedUserIds == null ? List.of() : mentionedUserIds)
                .recipientUserIds(recipientUserIds == null ? List.of() : recipientUserIds)
                .isDirect(isDirect)
                .build();
    }

    private UserBasicProfile safeGetBasicProfile(UUID userId) {
        try {
            if (userId == null) return null;
            var response = userClient.getUsersBulk(List.of(userId));
            if (response == null || response.getData() == null) return null;
            return response.getData().stream()
                    .filter(p -> p != null && userId.equals(p.getAccountId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
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
