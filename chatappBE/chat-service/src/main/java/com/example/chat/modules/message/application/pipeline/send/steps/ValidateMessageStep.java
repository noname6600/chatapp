package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ValidateMessageStep
        implements PipelineStep<SendMessageContext> {

    @Override
    public void execute(SendMessageContext context) {

        SendMessageRequest request = context.getRequest();

        if (request == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request cannot be null"
            );
        }

        UUID roomId = request.getRoomId();
        UUID senderId = request.getSenderId();

        if (roomId == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "roomId is required"
            );
        }

        if (senderId == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "senderId is required"
            );
        }

        boolean hasContent =
                request.getContent() != null &&
                        !request.getContent().isBlank();

        boolean hasAttachments =
                request.getAttachments() != null &&
                        !request.getAttachments().isEmpty();

        if (!hasContent && !hasAttachments) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Message must contain content or attachment"
            );
        }

        context.setRoomId(roomId);
        context.setSenderId(senderId);
    }
}