package com.example.chat.modules.message.application.command.impl;

import com.example.chat.modules.message.application.command.IMessageCommandService;
import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessagePipeline;
import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.chat.modules.message.application.pipeline.edit.EditMessagePipeline;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.application.pipeline.send.SendMessagePipeline;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageCommandService
        implements IMessageCommandService {

    private final SendMessagePipeline sendPipeline;
    private final EditMessagePipeline editPipeline;
    private final DeleteMessagePipeline deletePipeline;

    private final MessageMapper mapper;

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
        private final RoomMemberRepository roomMemberRepository;

    @Override
    public MessageResponse sendMessage(
            SendMessageRequest request
    ) {

        request.setMentionedUserIds(filterMentionedUsers(request));

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
    public void deleteMessage(
            DeleteMessageRequest request
    ) {

        DeleteMessageContext context =
                new DeleteMessageContext();

        context.setRequest(request);

        deletePipeline.execute(context);
    }
}