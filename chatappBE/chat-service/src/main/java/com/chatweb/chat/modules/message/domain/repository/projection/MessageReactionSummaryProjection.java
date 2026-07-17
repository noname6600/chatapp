package com.chatweb.chat.modules.message.domain.repository.projection;

import java.util.UUID;

public interface MessageReactionSummaryProjection {
    UUID getMessageId();
    String getEmoji();
    Long getCount();
    Boolean getReactedByMe();
}
