package com.chatweb.chat.modules.room.service;

import java.util.UUID;

public interface RoomMembershipGuard {

    void ensureRoomMember(UUID roomId, UUID userId);
}