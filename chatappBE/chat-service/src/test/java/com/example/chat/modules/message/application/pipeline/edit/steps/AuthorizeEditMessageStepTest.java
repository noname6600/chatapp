package com.example.chat.modules.message.application.pipeline.edit.steps;

import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizeEditMessageStepTest {

    private final AuthorizeEditMessageStep step = new AuthorizeEditMessageStep();

    @Test
    void execute_allowsSenderToEditOwnMessage() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        MessageAggregate aggregate = MessageAggregate.create(
                roomId,
                senderId,
                1L,
                "hello",
                null,
                List.of(),
                "client-1",
                null
        );

        EditMessageContext context = new EditMessageContext();
        context.setAggregate(aggregate);
        context.setRequest(EditMessageRequest.builder()
                .messageId(aggregate.getMessage().getId())
                .actorId(senderId)
                .content("updated")
                .build());

        assertThatCode(() -> step.execute(context)).doesNotThrowAnyException();
    }

    @Test
    void execute_rejectsNonSender() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        MessageAggregate aggregate = MessageAggregate.create(
                roomId,
                senderId,
                2L,
                "hello",
                null,
                List.of(),
                "client-2",
                null
        );

        EditMessageContext context = new EditMessageContext();
        context.setAggregate(aggregate);
        context.setRequest(EditMessageRequest.builder()
                .messageId(aggregate.getMessage().getId())
                .actorId(otherUserId)
                .content("updated")
                .build());

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
