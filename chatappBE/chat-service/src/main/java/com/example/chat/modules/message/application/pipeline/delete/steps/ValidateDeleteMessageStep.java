package com.example.chat.modules.message.application.pipeline.delete.steps;

import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessageContext;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
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
                    CommonErrorCode.VALIDATION_ERROR,"Request cannot be null"
            );
        }
    }
}


