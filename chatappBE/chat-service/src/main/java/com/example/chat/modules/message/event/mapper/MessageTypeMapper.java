package com.example.chat.modules.message.event.mapper;

import com.example.chat.modules.message.domain.enums.MessageType;

public final class MessageTypeMapper {

    private MessageTypeMapper() {}

    public static com.example.common.integration.enums.MessageType toEvent(
            MessageType type
    ) {

        if (type == null) {
            return null;
        }

        return com.example.common.integration.enums.MessageType.valueOf(type.name());
    }
}