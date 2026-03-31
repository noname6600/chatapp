package com.example.chat.modules.message.application.command;

import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;

public interface IMessageCommandService {

    MessageResponse sendMessage(SendMessageRequest request);

    MessageResponse editMessage(EditMessageRequest request);

    void deleteMessage(DeleteMessageRequest request);
}
