package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.dto.request.MessageBlockRequest;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.domain.enums.MessageBlockType;
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

                boolean hasRenderableBlocks =
                                request.getBlocks() != null &&
                                                request.getBlocks().stream().anyMatch(this::isRenderableBlock);

                if (!hasContent && !hasAttachments && !hasRenderableBlocks) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Message must contain content or attachment"
            );
        }

        context.setRoomId(roomId);
        context.setSenderId(senderId);
    }

        private boolean isRenderableBlock(MessageBlockRequest block) {
                if (block == null || block.getType() == null) {
                        return false;
                }

                return switch (block.getType()) {
                        case TEXT -> block.getText() != null && !block.getText().isBlank();
                        case ASSET -> block.getAttachment() != null;
                        case ROOM_INVITE ->
                                        block.getRoomInvite() != null && block.getRoomInvite().getRoomId() != null;
                };
        }
}