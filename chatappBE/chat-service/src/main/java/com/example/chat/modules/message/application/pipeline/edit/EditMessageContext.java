package com.example.chat.modules.message.application.pipeline.edit;

import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.model.MessageAggregate;
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
