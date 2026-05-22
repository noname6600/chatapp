package com.chatweb.chat.modules.message.application.pipeline.edit;

import com.chatweb.chat.modules.message.application.dto.request.EditMessageRequest;
import com.chatweb.chat.modules.message.domain.entity.ChatAttachment;
import com.chatweb.chat.modules.message.domain.entity.ChatMessage;
import com.chatweb.chat.modules.message.domain.model.MessageAggregate;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EditMessageContext {

    private EditMessageRequest request;

    private MessageAggregate aggregate;

    private ChatMessage savedMessage;

    private List<ChatAttachment> savedAttachments;

}
