package com.example.presence.websocket.session;

import java.util.Set;
import java.util.UUID;

public interface IPresenceQuery {
    boolean isUserOnline(UUID userId);
    Set<UUID> getOnlineUsers();
}
