package com.example.common.websocket.handshake;

import java.security.Principal;
import java.util.UUID;

public class WsPrincipal implements Principal {

    private final UUID userId;

    public WsPrincipal(UUID userId) {
        this.userId = userId;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public UUID getUserId() {
        return userId;
    }
}
