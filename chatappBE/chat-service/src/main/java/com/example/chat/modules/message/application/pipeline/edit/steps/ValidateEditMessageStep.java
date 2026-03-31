package com.example.chat.modules.message.application.pipeline.edit.steps;

import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ValidateEditMessageStep
        implements PipelineStep<EditMessageContext> {

    @Override
    public void execute(EditMessageContext context) {

        EditMessageRequest request = context.getRequest();

        if (request == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request cannot be null"
            );
        }

        if (request.getMessageId() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "messageId required"
            );
        }

        if (request.getActorId() == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "senderId required"
            );
        }

        if (request.getContent() == null ||
                request.getContent().isBlank()) {

            throw new BusinessException(
                    ErrorCode.MESSAGE_CONTENT_EMPTY
            );
        }
    }
}
