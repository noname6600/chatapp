package com.chatweb.chat.modules.message.event.mapper;

import com.chatweb.chat.modules.message.domain.enums.MessageType;

public final class MessageTypeMapper {

    private MessageTypeMapper() {}

    public static com.chatweb.common.integration.enums.MessageType toEvent(
            MessageType type
    ) {

        if (type == null) {
            return null;
        }

        return com.chatweb.common.integration.enums.MessageType.valueOf(type.name());
    }
}