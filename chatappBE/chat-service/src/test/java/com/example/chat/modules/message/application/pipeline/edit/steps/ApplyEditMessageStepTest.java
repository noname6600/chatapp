package com.example.chat.modules.message.application.pipeline.edit.steps;

import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyEditMessageStepTest {

    private final ApplyEditMessageStep step = new ApplyEditMessageStep();

    @Test
    void execute_updatesContentAndPreservesReplyContext() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID replyToMessageId = UUID.randomUUID();

        MessageAggregate aggregate = MessageAggregate.create(
                roomId,
                senderId,
                1L,
                "before",
                replyToMessageId,
                List.of(),
                "client-123",
                null
        );

        EditMessageContext context = new EditMessageContext();
        context.setAggregate(aggregate);
        context.setRequest(EditMessageRequest.builder()
                .messageId(aggregate.getMessage().getId())
                .actorId(senderId)
                .content("after")
                .build());

        step.execute(context);

        assertThat(aggregate.getMessage().getContent()).isEqualTo("after");
        assertThat(aggregate.getMessage().getReplyToMessageId()).isEqualTo(replyToMessageId);
        assertThat(aggregate.getMessage().getEditedAt()).isNotNull();
    }

    @Test
    void execute_preservesProvidedBlocksJson() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        MessageAggregate aggregate = MessageAggregate.create(
                roomId,
                senderId,
                1L,
                "before",
                null,
                List.of(),
                "client-123",
                null
        );

        String blocksJson = "[{\"type\":\"TEXT\",\"text\":\"A\"},{\"type\":\"ASSET\",\"attachment\":{\"type\":\"IMAGE\",\"url\":\"https://example.com/a.jpg\"}}]";

        EditMessageContext context = new EditMessageContext();
        context.setAggregate(aggregate);
        context.setRequest(EditMessageRequest.builder()
                .messageId(aggregate.getMessage().getId())
                .actorId(senderId)
                .content("A")
                .blocksJson(blocksJson)
                .build());

        step.execute(context);

        assertThat(aggregate.getMessage().getBlocksJson()).isEqualTo(blocksJson);
    }
}
