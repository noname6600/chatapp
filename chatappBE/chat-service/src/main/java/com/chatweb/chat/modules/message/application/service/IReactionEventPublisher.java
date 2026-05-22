package com.chatweb.chat.modules.message.application.service;

import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.common.integration.chat.ReactionPayload;

import java.util.List;

public interface IReactionEventPublisher {

    public void publishReactionUpdated(
            ReactionPayload payload
    );
}