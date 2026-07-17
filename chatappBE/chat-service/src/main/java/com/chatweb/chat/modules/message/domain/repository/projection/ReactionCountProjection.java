package com.chatweb.chat.modules.message.domain.repository.projection;

public interface ReactionCountProjection {
    String getEmoji();
    long getCount();
}
