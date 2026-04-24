package com.example.common.integration.chat;

import com.example.common.integration.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

import java.time.Instant;
import java.util.UUID;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public final class ChatMessagePayload {

    private final UUID messageId;

    private final UUID roomId;

    private final UUID senderId;

    private final Long seq;

    private final MessageType type;

    private final String content;

    private final UUID replyToMessageId;

    private final UUID forwardedFromMessageId;

    private final String systemEventType;

    private final UUID actorUserId;

    private final UUID targetMessageId;

    private final UUID replyToAuthorId;

    private final Instant createdAt;

    private final String senderDisplayName;

    private final String preview;

    private final List<AttachmentPayload> attachments;

    private final List<MessageBlockPayload> blocks;

    private final String clientMessageId;

    private final List<UUID> mentionedUserIds;

    private final List<UUID> recipientUserIds;

    /**
     * True when this message was sent in a direct (2-person PRIVATE) room.
     * Defaults to false when deserializing events from older producers that do not include this field.
     */
    private final boolean isDirect;

    @JsonCreator
    public ChatMessagePayload(
            @JsonProperty("messageId") UUID messageId,
            @JsonProperty("roomId") UUID roomId,
            @JsonProperty("senderId") UUID senderId,
            @JsonProperty("seq") Long seq,
            @JsonProperty("type") MessageType type,
            @JsonProperty("content") String content,
            @JsonProperty("replyToMessageId") UUID replyToMessageId,
            @JsonProperty("forwardedFromMessageId") UUID forwardedFromMessageId,
            @JsonProperty("systemEventType") String systemEventType,
            @JsonProperty("actorUserId") UUID actorUserId,
            @JsonProperty("targetMessageId") UUID targetMessageId,
            @JsonProperty("replyToAuthorId") UUID replyToAuthorId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("senderDisplayName") String senderDisplayName,
            @JsonProperty("preview") String preview,
            @JsonProperty("attachments") List<AttachmentPayload> attachments,
            @JsonProperty("blocks") List<MessageBlockPayload> blocks,
            @JsonProperty("clientMessageId") String clientMessageId,
            @JsonProperty("mentionedUserIds") List<UUID> mentionedUserIds,
            @JsonProperty("recipientUserIds") List<UUID> recipientUserIds,
            @JsonProperty("isDirect") boolean isDirect
    ) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.senderId = senderId;
        this.seq = seq;
        this.type = type;
        this.content = content;
        this.replyToMessageId = replyToMessageId;
        this.forwardedFromMessageId = forwardedFromMessageId;
        this.systemEventType = systemEventType;
        this.actorUserId = actorUserId;
        this.targetMessageId = targetMessageId;
        this.replyToAuthorId = replyToAuthorId;
        this.createdAt = createdAt;
        this.senderDisplayName = senderDisplayName;
        this.preview = preview;
        this.attachments = attachments;
        this.blocks = blocks;
        this.clientMessageId = clientMessageId;
        this.mentionedUserIds = mentionedUserIds;
        this.recipientUserIds = recipientUserIds;
        this.isDirect = isDirect;
    }
}
