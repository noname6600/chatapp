package com.chatweb.chat.modules.message.application.service;

import com.chatweb.chat.modules.message.domain.enums.SystemEventType;

import java.util.UUID;

public interface ISystemMessageService {

    void sendSystemMessage(
            UUID roomId,
            SystemEventType eventType,
            UUID actorUserId,
            UUID targetMessageId
    );
}
