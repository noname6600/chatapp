package com.chatweb.chat.modules.message.application.pipeline.reaction.steps;

import com.chatweb.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.entity.ChatReaction;
import com.chatweb.chat.modules.message.domain.repository.ChatMessageRepository;
import com.chatweb.chat.modules.message.domain.repository.ChatReactionRepository;
import com.chatweb.chat.modules.room.service.RoomMembershipGuard;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.core.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional
public class PersistReactionStep
        implements PipelineStep<ToggleReactionContext> {

    private final ChatMessageRepository messageRepository;
    private final ChatReactionRepository reactionRepository;
    private final RoomMembershipGuard roomMembershipGuard;

    @Override
    public void execute(ToggleReactionContext context) {

        ChatMessage message = messageRepository.findById(context.getMessageId())
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Message not found"
                ));

        roomMembershipGuard.ensureRoomMember(message.getRoomId(), context.getUserId());

        Optional<ChatReaction> existing =
                reactionRepository.findByMessageIdAndUserIdAndEmoji(
                        context.getMessageId(),
                        context.getUserId(),
                        context.getEmoji()
                );

        if (existing.isPresent()) {
            // Reaction exists â†’ delete it (toggle OFF)
            reactionRepository.deleteByMessageIdAndUserIdAndEmoji(
                    context.getMessageId(),
                    context.getUserId(),
                    context.getEmoji()
            );
            context.setRemoved(true);
            context.setReactionCreatedAt(existing.get().getCreatedAt());

        } else {
            // Reaction doesn't exist â†’ create it (toggle ON)
            ChatReaction reaction =
                    ChatReaction.builder()
                            .messageId(context.getMessageId())
                            .userId(context.getUserId())
                            .emoji(context.getEmoji())
                            .build();

            ChatReaction saved = reactionRepository.save(reaction);
            context.setRemoved(false);
            context.setReactionCreatedAt(saved.getCreatedAt());
        }
    }
}