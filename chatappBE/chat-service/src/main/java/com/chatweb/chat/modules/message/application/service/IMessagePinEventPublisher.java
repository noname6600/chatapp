package com.chatweb.chat.modules.message.application.service;

import java.time.Instant;
import java.util.UUID;

public interface IMessagePinEventPublisher {

    void publishMessagePinned(UUID roomId, UUID messageId, UUID actorId, Instant pinnedAt);

    void publishMessageUnpinned(UUID roomId, UUID messageId, UUID actorId, Instant unpinnedAt);
}
