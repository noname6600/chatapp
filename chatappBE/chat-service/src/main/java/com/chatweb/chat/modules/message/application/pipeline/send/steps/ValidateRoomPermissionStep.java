package com.chatweb.chat.modules.message.application.pipeline.send.steps;

import com.chatweb.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.chatweb.chat.modules.message.application.port.RoomPermissionService;
import com.chatweb.common.core.pipeline.PipelineStep;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ValidateRoomPermissionStep
        implements PipelineStep<SendMessageContext> {

    private final RoomPermissionService roomPermissionService;

    @Override
    public void execute(SendMessageContext context) {

        boolean allowed =
                roomPermissionService.canSendMessage(
                        context.getRoomId(),
                        context.getSenderId()
                );

        if (!allowed) {
            throw new BusinessException(
                    CommonErrorCode.FORBIDDEN,
                    "User cannot send message to this room"
            );
        }
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{
                ValidateMessageStep.class
        };
    }
}


