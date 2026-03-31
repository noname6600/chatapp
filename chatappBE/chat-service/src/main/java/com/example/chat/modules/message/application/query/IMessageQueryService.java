package com.example.chat.modules.message.application.query;

import com.example.chat.modules.message.application.dto.response.MessagePage;
import com.example.chat.modules.message.application.dto.response.MessageResponse;

import java.util.List;
import java.util.UUID;

public interface IMessageQueryService {

    MessagePage getLatestMessages(
            UUID currentUserId,
            UUID roomId,
            int limit
    );

    MessagePage getMessagesBefore(
            UUID currentUserId,
            UUID roomId,
            long beforeSeq,
            int limit
    );

    List<MessageResponse> getMessagesAround(
            UUID currentUserId,
            UUID roomId,
            UUID messageId,
            int halfWindow
    );

    List<MessageResponse> getMessageRange(
            UUID currentUserId,
            UUID roomId,
            long startSeq,
            long endSeq
    );

}
