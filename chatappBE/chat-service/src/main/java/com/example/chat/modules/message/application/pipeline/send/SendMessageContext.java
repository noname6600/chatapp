package com.example.chat.modules.message.application.pipeline.send;

import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.model.AttachmentDraft;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SendMessageContext {

    private SendMessageRequest request;

    private UUID roomId;

    private UUID senderId;

    private long seq;


    private MessageAggregate aggregate;

    private ChatMessage savedMessage;

    private List<ChatAttachment> savedAttachments;

    private List<AttachmentDraft> attachmentDrafts = List.of();

    private List<UUID> mentionedUsers = List.of();

}