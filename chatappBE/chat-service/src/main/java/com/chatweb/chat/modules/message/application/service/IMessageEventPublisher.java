package com.chatweb.chat.modules.message.application.service;

import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface IMessageEventPublisher {

    void publishMessageCreated(ChatMessage message, List<ChatAttachment> attachments, List<UUID> mentionedUserIds);

    void publishMessageEdited(ChatMessage message);

    void publishMessageDeleted(ChatMessage message);
}