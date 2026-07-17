package com.chatweb.chat.modules.message.application.command;

import com.chatweb.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.chatweb.chat.modules.message.application.dto.request.EditMessageRequest;
import com.chatweb.chat.modules.message.application.dto.request.ForwardMessageRequest;
import com.chatweb.chat.modules.message.application.dto.response.MessageResponse;
import com.chatweb.chat.modules.message.application.dto.request.SendMessageRequest;

public interface IMessageCommandService {

    MessageResponse sendMessage(SendMessageRequest request);

    MessageResponse editMessage(EditMessageRequest request);

    MessageResponse forwardMessage(ForwardMessageRequest request);

    void deleteMessage(DeleteMessageRequest request);
}
