package com.chatweb.presence.authorization;

import java.util.UUID;

public interface RoomAuthorizationService {

    void ensureRoomMember(UUID roomId, String accessToken);
}