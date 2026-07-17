package com.chatweb.chat.modules.message.event.factory;

import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import org.springframework.stereotype.Component;

@Component
public class MessageUpdatedPayloadFactory {

    public MessageUpdatedPayload from(ChatMessage message) {

        return MessageUpdatedPayload.builder()
                .messageId(message.getId())
                .roomId(message.getRoomId())
                .seq(message.getSeq())
                .content(message.getContent())
                .editedAt(message.getEditedAt())
                .build();
    }
}
