package com.example.chat.modules.message.application.command.impl;

import com.example.chat.modules.message.application.command.IMessageCommandService;
import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.dto.request.ForwardMessageRequest;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessagePipeline;
import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.chat.modules.message.application.pipeline.edit.EditMessagePipeline;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.application.pipeline.send.SendMessagePipeline;
import com.example.chat.modules.message.domain.enums.MessageBlockType;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageCommandService
        implements IMessageCommandService {

    private final SendMessagePipeline sendPipeline;
    private final EditMessagePipeline editPipeline;
    private final DeleteMessagePipeline deletePipeline;

    private final MessageMapper mapper;
        private final IMessageEventPublisher messageEventPublisher;

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
        private final RoomMemberRepository roomMemberRepository;
        private final RoomRepository roomRepository;

    @Override
    public MessageResponse sendMessage(
            SendMessageRequest request
    ) {

        request.setMentionedUserIds(filterMentionedUsers(request));
        validateInviteBlocks(request);

        String clientMessageId = request.getClientMessageId();
        if (clientMessageId != null) {
            ChatMessage existing = messageRepository
                    .findByRoomIdAndClientMessageId(request.getRoomId(), clientMessageId)
                    .orElse(null);
            if (existing != null) {
                List<ChatAttachment> attachments =
                        attachmentRepository.findByMessageId(existing.getId());
                return mapper.toResponse(existing, attachments, Collections.emptyList());
            }
        }

        SendMessageContext context = new SendMessageContext();
        context.setRequest(request);
        sendPipeline.execute(context);

        ChatMessage savedMessage = context.getSavedMessage();
        if (savedMessage == null) {
            throw new IllegalStateException(
                    "Saved message missing after send pipeline"
            );
        }

        List<ChatAttachment> attachments = context.getSavedAttachments();
        if (attachments == null) {
            attachments = Collections.emptyList();
        }

        return mapper.toResponse(
                savedMessage,
                attachments,
                Collections.emptyList()
        );
    }

        private void validateInviteBlocks(SendMessageRequest request) {
                if (request.getBlocks() == null || request.getBlocks().isEmpty()) {
                        return;
                }

                UUID senderId = request.getSenderId();

                request.getBlocks().stream()
                                .filter(Objects::nonNull)
                                .filter(block -> block.getType() == MessageBlockType.ROOM_INVITE)
                                .forEach(block -> {
                                        if (block.getRoomInvite() == null || block.getRoomInvite().getRoomId() == null) {
                                                throw new BusinessException(
                                                                CommonErrorCode.VALIDATION_ERROR,
                                                                "ROOM_INVITE block requires roomId"
                                                );
                                        }

                                        UUID inviteRoomId = block.getRoomInvite().getRoomId();
                                        Room room = roomRepository.findById(inviteRoomId)
                                                        .orElseThrow(() -> new BusinessException(
                                                                        CommonErrorCode.RESOURCE_NOT_FOUND,
                                                                        "Invite room not found"
                                                        ));

                                        if (room.getType() != RoomType.GROUP) {
                                                throw new BusinessException(
                                                                CommonErrorCode.BAD_REQUEST,
                                                                "Only group rooms can be invited"
                                                );
                                        }

                                        if (!roomMemberRepository.existsByRoomIdAndUserId(inviteRoomId, senderId)) {
                                                throw new BusinessException(
                                                                CommonErrorCode.FORBIDDEN,
                                                                "You cannot invite others to a room you are not a member of"
                                                );
                                        }

                                        block.getRoomInvite().setRoomName(room.getName());
                                        block.getRoomInvite().setRoomAvatarUrl(room.getAvatarUrl());
                                        block.getRoomInvite().setMemberCount((int) roomMemberRepository.countByRoomId(inviteRoomId));
                                });
        }

        private List<UUID> filterMentionedUsers(SendMessageRequest request) {
                List<UUID> mentioned = request.getMentionedUserIds();
                if (mentioned == null || mentioned.isEmpty()) {
                        return List.of();
                }

                UUID roomId = request.getRoomId();

                return mentioned.stream()
                                .filter(userId -> roomMemberRepository.existsByRoomIdAndUserId(roomId, userId))
                                .distinct()
                                .toList();
        }

    @Override
    public MessageResponse editMessage(
            EditMessageRequest request
    ) {

        EditMessageContext context =
                new EditMessageContext();

        context.setRequest(request);

        editPipeline.execute(context);

        ChatMessage savedMessage = context.getSavedMessage();
        if (savedMessage == null) {
            throw new IllegalStateException(
                    "Saved message missing after edit pipeline"
            );
        }

        List<ChatAttachment> attachments = context.getSavedAttachments();
        if (attachments == null) {
            attachments = Collections.emptyList();
        }

        return mapper.toResponse(
                savedMessage,
                attachments,
                Collections.emptyList()
        );
    }

    @Override
    @Transactional
    public MessageResponse forwardMessage(
            ForwardMessageRequest request
    ) {
        UUID actorId = request.getActorId();
        UUID targetRoomId = request.getTargetRoomId();

        if (!roomMemberRepository.existsByRoomIdAndUserId(targetRoomId, actorId)) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "Not a member of target room"
            );
        }

        ChatMessage sourceMessage = messageRepository.findById(request.getSourceMessageId())
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Source message not found"
                ));

        if (Boolean.TRUE.equals(sourceMessage.getDeleted())) {
            throw new BusinessException(
                    CommonErrorCode.BAD_REQUEST,
                    "Cannot forward a deleted message"
            );
        }

        Long nextSeq = Objects.requireNonNullElse(
                messageRepository.findMaxSeqByRoomId(targetRoomId),
                0L
        ) + 1;

        ChatMessage forwardedMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(targetRoomId)
                .senderId(actorId)
                .seq(nextSeq)
                .type(sourceMessage.getType())
                .content(sourceMessage.getContent())
                .blocksJson(sourceMessage.getBlocksJson())
                .forwardedFromMessageId(sourceMessage.getId())
                .deleted(false)
                .build();

        messageRepository.save(forwardedMessage);

        List<ChatAttachment> sourceAttachments = attachmentRepository.findByMessageId(sourceMessage.getId());
        List<ChatAttachment> forwardedAttachments = sourceAttachments.stream()
                .map(attachment -> ChatAttachment.builder()
                        .messageId(forwardedMessage.getId())
                        .type(attachment.getType())
                        .url(attachment.getUrl())
                        .publicId(attachment.getPublicId())
                        .fileName(attachment.getFileName())
                        .size(attachment.getSize())
                        .width(attachment.getWidth())
                        .height(attachment.getHeight())
                        .duration(attachment.getDuration())
                        .build())
                .toList();

        if (!forwardedAttachments.isEmpty()) {
            attachmentRepository.saveAll(forwardedAttachments);
        }

        messageEventPublisher.publishMessageCreated(
                forwardedMessage,
                forwardedAttachments,
                List.of()
        );

        return mapper.toResponse(forwardedMessage, forwardedAttachments, List.of());
    }

    @Override
    public void deleteMessage(
            DeleteMessageRequest request
    ) {

        DeleteMessageContext context =
                new DeleteMessageContext();

        context.setRequest(request);

        deletePipeline.execute(context);
    }
}

