package com.example.chat.modules.message.application.pipeline.reaction.steps;

import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ValidateReactionStep
        implements PipelineStep<ToggleReactionContext> {

    @Override
    public void execute(ToggleReactionContext context) {

        if (context.getMessageId() == null) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "messageId is required"
            );
        }

        if (context.getUserId() == null) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "userId is required"
            );
        }

        if (context.getEmoji() == null || context.getEmoji().isBlank()) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "emoji is required"
            );
        }
    }
}

