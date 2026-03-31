package com.example.common.websocket.handshake;

import java.util.UUID;

public interface IJwtHandshakeInterceptor {

    UUID resolveUserId(String token);
}


