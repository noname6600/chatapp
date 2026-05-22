package com.chatweb.chat.modules.message.event.mapper;

import com.chatweb.chat.modules.message.domain.enums.AttachmentType;

public final class AttachmentTypeMapper {

    private AttachmentTypeMapper(){}

    public static com.chatweb.common.integration.enums.AttachmentType toEvent(
            AttachmentType type
    ) {
        if (type == null) {
            return null;
        }

        return com.chatweb.common.integration.enums.AttachmentType.valueOf(type.name());
    }
}
