package com.chatweb.chat.modules.message.application.query;

import com.chatweb.chat.modules.message.application.dto.response.MessagePage;
import com.chatweb.chat.modules.message.application.dto.response.MessageResponse;
import com.chatweb.chat.modules.message.application.mapper.MessageMapper;
import com.chatweb.chat.modules.room.service.RoomMembershipGuard;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.chatweb.chat.modules.message.domain.repository.ChatMessageRepository;
import com.chatweb.chat.modules.message.domain.repository.ChatReactionRepository;
import com.chatweb.chat.modules.message.domain.repository.projection.MessageReactionSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageQueryService
        implements IMessageQueryService {

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatReactionRepository reactionRepository;
    private final MessageMapper mapper;
        private final RoomMembershipGuard roomMembershipGuard;

    @Override
    public MessagePage getLatestMessages(
            UUID currentUserId,
            UUID roomId,
            int limit
    ) {
                roomMembershipGuard.ensureRoomMember(roomId, currentUserId);

        // +1 Ä‘á»ƒ check hasMore
        List<ChatMessage> messages =
                messageRepository.findLatestByRoom(
                        roomId,
                        limit + 1
                );

        boolean hasMore = messages.size() > limit;

        if (hasMore) {
                        // findLatestByRoom returns ASC-ordered rows. When we fetch limit+1,
                        // drop the oldest extra row and keep the newest `limit` messages.
                        messages = messages.subList(messages.size() - limit, messages.size());
        }

        List<MessageResponse> responses =
                buildResponses(messages, currentUserId);

        return MessagePage.builder()
                .messages(responses)
                .hasMore(hasMore)
                .build();
    }

    @Override
    public MessagePage getMessagesBefore(
            UUID currentUserId,
            UUID roomId,
            long beforeSeq,
            int limit
    ) {
                roomMembershipGuard.ensureRoomMember(roomId, currentUserId);

        List<ChatMessage> messages =
                messageRepository.findBeforeSeq(
                        roomId,
                        beforeSeq,
                        limit + 1
                );

        boolean hasMore = messages.size() > limit;

        if (hasMore) {
                        // findBeforeSeq returns ASC-ordered rows nearest to beforeSeq.
                        // Keep the newest `limit` rows in this window and drop only the
                        // oldest extra row used for hasMore detection.
                        messages = messages.subList(messages.size() - limit, messages.size());
        }

        List<MessageResponse> responses =
                buildResponses(messages, currentUserId);

        return MessagePage.builder()
                .messages(responses)
                .hasMore(hasMore)
                .build();
    }


    @Override
    public List<MessageResponse> getMessagesAround(
            UUID currentUserId,
            UUID roomId,
            UUID messageId,
            int halfWindow
    ) {
                roomMembershipGuard.ensureRoomMember(roomId, currentUserId);

        ChatMessage target = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Message not found"));

                if (!roomId.equals(target.getRoomId())) {
                        throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Message does not belong to the room");
                }

        long startSeq = Math.max(1, target.getSeq() - halfWindow);
        long endSeq = target.getSeq() + halfWindow;

        List<ChatMessage> messages = messageRepository.findRange(roomId, startSeq, endSeq);
        return buildResponses(messages, currentUserId);
    }

    @Override
    public List<MessageResponse> getMessageRange(
            UUID currentUserId,
            UUID roomId,
            long startSeq,
            long endSeq
    ) {
                roomMembershipGuard.ensureRoomMember(roomId, currentUserId);

        List<ChatMessage> messages =
                messageRepository.findRange(
                        roomId,
                        startSeq,
                        endSeq
                );

                return buildResponses(messages, currentUserId);
    }

    private List<MessageResponse> buildResponses(
                        List<ChatMessage> messages,
                        UUID currentUserId
    ) {

        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<UUID> messageIds =
                messages.stream()
                        .map(ChatMessage::getId)
                        .toList();

        Map<UUID, List<ChatAttachment>> attachments =
                attachmentRepository
                        .findByMessageIdIn(messageIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                ChatAttachment::getMessageId
                        ));

        Map<UUID, List<MessageReactionSummaryProjection>> reactions =
                reactionRepository
                        .summarizeReactionsForMessages(messageIds, currentUserId)
                        .stream()
                        .collect(Collectors.groupingBy(
                                MessageReactionSummaryProjection::getMessageId
                        ));

        return messages.stream()
                .map(message -> mapper.toResponse(
                        message,
                        attachments.getOrDefault(
                                message.getId(),
                                List.of()
                        ),
                        reactions.getOrDefault(
                                message.getId(),
                                List.of()
                        )
                ))
                .collect(Collectors.toList());
    }
}

