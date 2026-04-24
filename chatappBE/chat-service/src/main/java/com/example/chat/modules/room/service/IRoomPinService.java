package com.example.chat.modules.room.service;

import com.example.chat.modules.message.application.dto.response.MessageResponse;

import java.util.List;
import java.util.UUID;

public interface IRoomPinService {

    void pinMessage(UUID roomId, UUID actorId, UUID messageId);

    void unpinMessage(UUID roomId, UUID actorId, UUID messageId);

    List<MessageResponse> getPinnedMessages(UUID roomId, UUID actorId);
}
