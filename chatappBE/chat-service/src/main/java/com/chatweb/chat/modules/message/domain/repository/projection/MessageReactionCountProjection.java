package com.chatweb.chat.modules.message.domain.repository.projection;

import java.util.UUID;

public interface MessageReactionCountProjection {
    UUID getMessageId();
    String getEmoji();
    Long getCount();
}
