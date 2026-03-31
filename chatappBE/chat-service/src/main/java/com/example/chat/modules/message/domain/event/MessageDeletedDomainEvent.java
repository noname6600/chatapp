package com.example.chat.modules.message.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public final class MessageDeletedDomainEvent {

    private final UUID messageId;

    private final UUID roomId;

    private final UUID deletedBy;

    private final Long seq;

    @Builder.Default
    private final Instant occurredAt = Instant.now();
}