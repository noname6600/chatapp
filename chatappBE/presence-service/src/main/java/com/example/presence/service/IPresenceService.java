package com.example.presence.service;

import com.example.common.integration.presence.PresenceMode;
import com.example.common.integration.presence.PresenceStatus;
import com.example.common.integration.presence.PresenceUserStatePayload;
import com.example.presence.dto.PresenceSelfResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface IPresenceService {
    void online(UUID userId);
    void heartbeat(UUID userId, boolean active);
    void offline(UUID userId);
    void handleUserOfflineByTTL(UUID userId);
    void updatePresence(UUID userId, PresenceMode mode, PresenceStatus status);
    PresenceSelfResponse getSelfPresence(UUID userId);
    void joinRoom(UUID roomId, UUID userId);
    void leaveRoom(UUID roomId, UUID userId);
    List<PresenceUserStatePayload> getAllPresenceUsers();
    List<PresenceUserStatePayload> getRoomPresence(UUID roomId);
    void notifyRoomOnlineUsers(UUID roomId);
}
