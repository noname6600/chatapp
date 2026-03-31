package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.mapper.MessageBlockMapper;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateMessageAggregateStepTest {

    private final CreateMessageAggregateStep step = new CreateMessageAggregateStep(
            new MessageBlockMapper(new ObjectMapper())
    );

    @Test
    void execute_setsClientMessageIdOnAggregate() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        String clientMessageId = "test-client-id-42";

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("hello world")
                .clientMessageId(clientMessageId)
                .build();

        SendMessageContext context = new SendMessageContext();
        context.setRequest(request);
        context.setRoomId(roomId);
        context.setSenderId(senderId);
        context.setSeq(1L);
        context.setAttachmentDrafts(List.of());

        step.execute(context);

        MessageAggregate aggregate = context.getAggregate();
        assertThat(aggregate).isNotNull();
        assertThat(aggregate.getMessage().getClientMessageId()).isEqualTo(clientMessageId);
    }

    @Test
    void execute_withNullClientMessageId_aggregateHasNullClientMessageId() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content("hello world")
                .build();

        SendMessageContext context = new SendMessageContext();
        context.setRequest(request);
        context.setRoomId(roomId);
        context.setSenderId(senderId);
        context.setSeq(2L);
        context.setAttachmentDrafts(List.of());

        step.execute(context);

        assertThat(context.getAggregate().getMessage().getClientMessageId()).isNull();
    }
}
