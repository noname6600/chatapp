package com.chatweb.chat.modules.message.application.pipeline.edit.steps;

import com.chatweb.chat.modules.message.application.dto.request.EditMessageRequest;
import com.chatweb.chat.modules.message.application.pipeline.edit.EditMessageContext;
import com.chatweb.chat.exception.ChatErrorCode;
import com.chatweb.common.core.pipeline.PipelineStep;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ValidateEditMessageStep
        implements PipelineStep<EditMessageContext> {

    @Override
    public void execute(EditMessageContext context) {

        EditMessageRequest request = context.getRequest();

        if (request == null) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "Request cannot be null"
            );
        }

        if (request.getMessageId() == null) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "messageId required"
            );
        }

        if (request.getActorId() == null) {
            throw new BusinessException(
                    CommonErrorCode.VALIDATION_ERROR,
                    "senderId required"
            );
        }

        if (request.getContent() == null ||
                request.getContent().isBlank()) {

            throw new BusinessException(
                    ChatErrorCode.MESSAGE_CONTENT_EMPTY
            );
        }
    }
}


