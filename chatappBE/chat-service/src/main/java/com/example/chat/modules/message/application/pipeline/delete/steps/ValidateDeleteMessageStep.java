package com.example.chat.modules.message.application.pipeline.delete.steps;

import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ValidateDeleteMessageStep
        implements PipelineStep<DeleteMessageContext> {

    @Override
    public void execute(DeleteMessageContext context) {

        DeleteMessageRequest request =
                context.getRequest();

        if (request == null ||
                request.getMessageId() == null) {

            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,"Request cannot be null"
            );
        }
    }
}
