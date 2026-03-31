package com.example.chat.modules.message.application.pipeline.delete;

import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.domain.model.MessageAggregate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteMessageContext {

    private DeleteMessageRequest request;

    private MessageAggregate aggregate;

}
