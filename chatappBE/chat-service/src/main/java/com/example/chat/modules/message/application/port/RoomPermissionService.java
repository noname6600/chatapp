package com.example.chat.modules.message.application.port;

import java.util.UUID;

public interface RoomPermissionService {

    boolean canSendMessage(UUID roomId, UUID userId);

}