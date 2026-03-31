package com.example.chat.modules.message.domain.service;

import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;

import java.util.List;

public interface IMessagePreviewService {

    String buildPreview(
            ChatMessage message,
            List<ChatAttachment> attachments
    );
}