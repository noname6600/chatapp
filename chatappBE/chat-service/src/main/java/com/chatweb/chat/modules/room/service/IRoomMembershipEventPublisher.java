package com.chatweb.chat.modules.room.service;

import java.time.Instant;
import java.util.UUID;

public interface IRoomMembershipEventPublisher {

    void publishMemberJoined(UUID roomId, UUID userId, String role, Instant joinedAt);

    void publishMemberLeft(UUID roomId, UUID userId);

    void publishMemberRemoved(UUID roomId, UUID userId);
}
