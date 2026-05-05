package com.example.presence.state.port;

import java.util.Set;
import java.util.UUID;

public interface PresenceEphemeralStatePort {

    void addOnlineUser(UUID userId);

    void removeOnlineUser(UUID userId);

    Set<UUID> getOnlineUsers();

    void addUserToRoom(UUID roomId, UUID userId);

    void removeUserFromRoom(UUID roomId, UUID userId);

    Set<UUID> getRoomUsers(UUID roomId);

    Set<UUID> getUserRooms(UUID userId);

    void clearUserRooms(UUID userId);
}
