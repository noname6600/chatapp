package com.example.common.websocket.session;

import java.util.Set;
import java.util.UUID;

public interface IPresenceQuery {
    boolean isUserOnline(UUID userId);
    Set<UUID> getOnlineUsers();
}

