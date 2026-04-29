package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.dto.request.MessageBlockRequest;
import com.example.chat.modules.message.application.dto.request.RoomInviteRequest;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.domain.enums.MessageBlockType;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateMessageStepTest {

    private final ValidateMessageStep step = new ValidateMessageStep();

    @Test
    void execute_withRoomInviteBlockOnly_acceptsMessage() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID inviteRoomId = UUID.randomUUID();

        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .blocks(List.of(MessageBlockRequest.builder()
                        .type(MessageBlockType.ROOM_INVITE)
                        .roomInvite(RoomInviteRequest.builder().roomId(inviteRoomId).build())
                        .build()))
                .build();

        SendMessageContext context = new SendMessageContext();
        context.setRequest(request);

        step.execute(context);

        assertThat(context.getRoomId()).isEqualTo(roomId);
        assertThat(context.getSenderId()).isEqualTo(senderId);
    }

    @Test
    void execute_withInvalidRoomInviteBlockOnly_rejectsMessage() {
        SendMessageRequest request = SendMessageRequest.builder()
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .blocks(List.of(MessageBlockRequest.builder()
                        .type(MessageBlockType.ROOM_INVITE)
                        .roomInvite(RoomInviteRequest.builder().build())
                        .build()))
                .build();

        SendMessageContext context = new SendMessageContext();
        context.setRequest(request);

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);
    }
}


