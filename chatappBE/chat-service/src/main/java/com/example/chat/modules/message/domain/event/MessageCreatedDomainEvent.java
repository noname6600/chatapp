package com.example.chat.modules.message.domain.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public final class MessageCreatedDomainEvent {

    private final UUID messageId;

    private final UUID roomId;

    private final UUID senderId;

    private final Long seq;

    @Builder.Default
    private final Instant occurredAt = Instant.now();
}