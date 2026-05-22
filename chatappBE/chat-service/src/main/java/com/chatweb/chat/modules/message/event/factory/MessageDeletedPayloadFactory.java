package com.chatweb.chat.modules.message.event.factory;

import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.common.integration.chat.MessageDeletedPayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MessageDeletedPayloadFactory {

    public MessageDeletedPayload from(
            ChatMessage message,
            UUID deletedBy
    ) {

        return MessageDeletedPayload.builder()
                .messageId(message.getId())
                .roomId(message.getRoomId())
                .seq(message.getSeq())
                .deletedAt(Instant.now())
                .deletedBy(deletedBy)
                .build();
    }
}
