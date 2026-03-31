package com.example.chat.modules.message.application.service;

import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.common.integration.chat.ReactionPayload;

import java.util.List;

public interface IReactionEventPublisher {

    public void publishReactionUpdated(
            ReactionPayload payload
    );
}