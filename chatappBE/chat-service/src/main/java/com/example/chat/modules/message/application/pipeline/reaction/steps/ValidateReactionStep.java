package com.example.chat.modules.message.application.pipeline.reaction.steps;

import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ValidateReactionStep
        implements PipelineStep<ToggleReactionContext> {

    @Override
    public void execute(ToggleReactionContext context) {

        if (context.getMessageId() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "messageId is required"
            );
        }

        if (context.getUserId() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "userId is required"
            );
        }

        if (context.getEmoji() == null || context.getEmoji().isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "emoji is required"
            );
        }
    }
}