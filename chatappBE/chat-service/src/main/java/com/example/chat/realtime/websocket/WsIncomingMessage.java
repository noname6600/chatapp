package com.example.chat.realtime.websocket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.UUID;

@Getter
public class WsIncomingMessage {

    private final WsCommandType type;

    private final UUID roomId;

    private final UUID messageId;

    private final String content;

    private final String reaction;

    private final UUID replyToMessageId;

    private final String clientMessageId;

    @JsonCreator
    public WsIncomingMessage(

            @JsonProperty("type")
            WsCommandType type,

            @JsonProperty("roomId")
            UUID roomId,

            @JsonProperty("messageId")
            UUID messageId,

            @JsonProperty("content")
            String content,

            @JsonProperty("reaction")
            String reaction,

            @JsonProperty("replyToMessageId")
            UUID replyToMessageId,

            @JsonProperty("clientMessageId")
            String clientMessageId
    ) {

        this.type = type;
        this.roomId = roomId;
        this.messageId = messageId;
        this.content = content;
        this.reaction = reaction;
        this.replyToMessageId = replyToMessageId;
        this.clientMessageId = clientMessageId;
    }
}