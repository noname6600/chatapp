package com.chatweb.chat.modules.message.application.pipeline.send;

import com.chatweb.chat.modules.message.application.dto.request.SendMessageRequest;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.model.AttachmentDraft;
import com.chatweb.chat.modules.message.domain.model.MessageAggregate;
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