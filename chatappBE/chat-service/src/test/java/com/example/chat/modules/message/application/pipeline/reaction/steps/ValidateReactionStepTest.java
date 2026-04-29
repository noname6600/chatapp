package com.example.chat.modules.message.application.pipeline.reaction.steps;

import com.example.chat.modules.message.application.pipeline.reaction.ToggleReactionContext;
import com.example.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ValidateReactionStepTest {

    private final ValidateReactionStep step = new ValidateReactionStep();

    @Test
    void execute_validContextWithoutRoomId_doesNotThrow() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setMessageId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setEmoji("🔥");

        assertThatCode(() -> step.execute(context)).doesNotThrowAnyException();
    }

    @Test
    void execute_withoutMessageId_throwsValidationError() {
        ToggleReactionContext context = new ToggleReactionContext();
        context.setUserId(UUID.randomUUID());
        context.setEmoji("🔥");

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("messageId is required");
    }
}

