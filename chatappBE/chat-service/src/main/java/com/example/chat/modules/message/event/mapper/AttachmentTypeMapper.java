package com.example.chat.modules.message.event.mapper;

import com.example.chat.modules.message.domain.enums.AttachmentType;

public final class AttachmentTypeMapper {

    private AttachmentTypeMapper(){}

    public static com.example.common.integration.enums.AttachmentType toEvent(
            AttachmentType type
    ) {
        if (type == null) {
            return null;
        }

        return com.example.common.integration.enums.AttachmentType.valueOf(type.name());
    }
}
