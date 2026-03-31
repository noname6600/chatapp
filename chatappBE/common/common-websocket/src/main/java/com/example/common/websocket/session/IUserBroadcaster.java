package com.example.common.websocket.session;

import java.util.UUID;

public interface IUserBroadcaster {

    void sendToUser(UUID userId, Object payload);
}
